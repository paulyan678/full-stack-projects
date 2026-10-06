package com.laioffer.onlineorder.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.laioffer.onlineorder.entity.CartEntity;
import com.laioffer.onlineorder.entity.CustomerEntity;
import com.laioffer.onlineorder.entity.MenuItemEntity;
import com.laioffer.onlineorder.repository.CartRepository;
import com.laioffer.onlineorder.repository.CustomerRepository;
import com.laioffer.onlineorder.repository.MenuItemRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("postgres")
@SpringBootTest
@Import(CartPostgresIntegrationTest.BarrierConfiguration.class)
class CartPostgresIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("POSTGRES_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("POSTGRES_TEST_USER", "postgres"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("POSTGRES_TEST_PASSWORD", "postgres"));
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("logging.level.root", () -> "WARN");
    }

    @Autowired CartService service;
    @Autowired CustomerRepository customers;
    @Autowired MenuItemRepository menus;
    @Autowired CartRepository carts;
    @Autowired QueryBarrier barrier;
    @Autowired JdbcTemplate jdbc;
    private long customerId;
    private MenuItemEntity item;

    @BeforeEach
    void createCustomer() {
        customerId = customers.save(new CustomerEntity(null, UUID.randomUUID() + "@example.test",
                "unused-test-password", true, "Concurrent", "Customer")).id();
        carts.save(new CartEntity(null, customerId, BigDecimal.ZERO));
        item = menus.findAll().getFirst();
    }

    @AfterEach
    void removeCustomer() {
        barrier.clear();
        customers.deleteById(customerId);
    }

    @Test
    void concurrentAddsPreserveQuantityAndExactTotal() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> writes = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                writes.add(executor.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    service.addMenuItemToCart(customerId, item.id());
                    return null;
                }));
            }
            start.countDown();
            for (var write : writes) write.get(15, TimeUnit.SECONDS);
        }
        var cart = service.getCart(customerId);
        assertThat(cart.totalPrice()).isEqualByComparingTo(item.price().multiply(new BigDecimal("24")));
        assertThat(cart.orderItems()).singleElement().satisfies(row -> assertThat(row.quantity()).isEqualTo(24));
    }

    @Test
    void checkoutBetweenReadQueriesDoesNotMixDatabaseSnapshots() throws Exception {
        service.addMenuItemToCart(customerId, item.id());
        barrier.arm("findByCustomerId", customerId);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> service.getCart(customerId));
            try {
                assertThat(barrier.captured.await(5, TimeUnit.SECONDS)).isTrue();
                service.clearCart(customerId);
            } finally {
                barrier.release.countDown();
            }
            var overlapping = pending.get(10, TimeUnit.SECONDS);
            assertThat(overlapping.totalPrice()).isEqualByComparingTo(item.price());
            assertThat(overlapping.orderItems()).singleElement()
                    .satisfies(row -> assertThat(row.lineTotal()).isEqualByComparingTo(item.price()));
            var afterCheckout = service.getCart(customerId);
            assertThat(afterCheckout.totalPrice()).isZero();
            assertThat(afterCheckout.orderItems()).isEmpty();
        }
    }

    @Test
    void addWaitsForCheckoutTransactionBeforeUpdatingCart() throws Exception {
        service.addMenuItemToCart(customerId, item.id());
        barrier.arm("findLockedByCustomerId", customerId);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var checkout = executor.submit(() -> service.clearCart(customerId));
            Future<?> add;
            try {
                assertThat(barrier.captured.await(5, TimeUnit.SECONDS)).isTrue();
                add = executor.submit(() -> service.addMenuItemToCart(customerId, item.id()));
                // Observe a real PostgreSQL lock wait before allowing checkout to commit.
                // Submitting a Future alone would also pass with sequential execution.
                boolean waitingOnCheckout = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!add.isDone() && System.nanoTime() < deadline) {
                    waitingOnCheckout = Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS (
                                SELECT 1 FROM pg_stat_activity
                                WHERE wait_event_type = 'Lock'
                                  AND ? = ANY(pg_blocking_pids(pid))
                                  AND query LIKE 'SELECT id, customer_id, total_price FROM carts%'
                            )
                            """, Boolean.class, barrier.backendPid));
                    if (waitingOnCheckout) break;
                    TimeUnit.MILLISECONDS.sleep(10);
                }
                assertThat(waitingOnCheckout).as("add must wait on checkout's cart row lock").isTrue();
                assertThat(add.isDone()).isFalse();
            } finally {
                barrier.release.countDown();
            }
            checkout.get(10, TimeUnit.SECONDS);
            add.get(10, TimeUnit.SECONDS);
        }
        var result = service.getCart(customerId);
        assertThat(result.totalPrice()).isEqualByComparingTo(item.price());
        assertThat(result.orderItems()).singleElement().satisfies(row -> assertThat(row.quantity()).isEqualTo(1));
    }

    static class QueryBarrier {
        volatile String method;
        volatile long customerId;
        volatile int backendPid;
        volatile CountDownLatch captured;
        volatile CountDownLatch release;
        final AtomicBoolean first = new AtomicBoolean();

        void arm(String method, long customerId) {
            this.customerId = customerId;
            captured = new CountDownLatch(1);
            release = new CountDownLatch(1);
            first.set(true);
            this.method = method;
        }

        void clear() {
            method = null;
        }
    }

    @TestConfiguration
    static class BarrierConfiguration {
        @Bean QueryBarrier queryBarrier() { return new QueryBarrier(); }

        @Bean
        static BeanPostProcessor repositoryBarrier(QueryBarrier barrier, JdbcTemplate jdbc) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof CartRepository)) return bean;
                    var proxy = new ProxyFactory(bean);
                    proxy.setInterfaces(CartRepository.class);
                    proxy.addAdvice((MethodInterceptor) invocation -> {
                        var result = invocation.proceed();
                        if (invocation.getMethod().getName().equals(barrier.method)
                                && invocation.getArguments()[0].equals(barrier.customerId)
                                && barrier.first.compareAndSet(true, false)) {
                            barrier.backendPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                            barrier.captured.countDown();
                            if (!barrier.release.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Query barrier timed out");
                            }
                        }
                        return result;
                    });
                    return proxy.getProxy();
                }
            };
        }
    }
}
