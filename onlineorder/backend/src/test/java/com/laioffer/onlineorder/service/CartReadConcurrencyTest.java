package com.laioffer.onlineorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.laioffer.onlineorder.entity.CartEntity;
import com.laioffer.onlineorder.repository.CartRepository;
import com.laioffer.onlineorder.repository.MenuItemRepository;
import com.laioffer.onlineorder.repository.OrderItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CartReadConcurrencyTest {
    @Configuration
    @EnableCaching
    static class TestConfiguration {
        @Bean CacheManager cacheManager() { return new CaffeineCacheManager("carts"); }
        @Bean CartRepository carts() { return mock(CartRepository.class); }
        @Bean MenuItemRepository menus() { return mock(MenuItemRepository.class); }
        @Bean OrderItemRepository orders() { return mock(OrderItemRepository.class); }
        @Bean CartService cartService(CartRepository carts, MenuItemRepository menus,
                                     OrderItemRepository orders) {
            return new CartService(carts, menus, orders);
        }
    }

    @Test
    void delayedReadCannotRestoreCartAfterCompletedCheckout() throws Exception {
        var database = new AtomicReference<>(new CartEntity(3L, 1L, new BigDecimal("10.00")));
        var captured = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstRead = new AtomicBoolean(true);
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class);
             var executor = Executors.newSingleThreadExecutor()) {
            var carts = context.getBean(CartRepository.class);
            when(carts.findByCustomerId(1L)).thenAnswer(invocation -> {
                var snapshot = database.get();
                if (firstRead.compareAndSet(true, false)) {
                    captured.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Delayed read was never released");
                    }
                }
                return Optional.of(snapshot);
            });
            when(carts.findLockedByCustomerId(1L)).thenAnswer(invocation -> Optional.of(database.get()));
            when(carts.updateTotalPrice(eq(3L), any())).thenAnswer(invocation -> {
                database.set(new CartEntity(3L, 1L, invocation.getArgument(1)));
                return 1;
            });
            when(context.getBean(OrderItemRepository.class).findAllByCartIdOrderByIdAsc(3L))
                    .thenReturn(List.of());
            when(context.getBean(MenuItemRepository.class).findAllById(anyList()))
                    .thenReturn(List.of());
            var service = context.getBean(CartService.class);
            var pending = executor.submit(() -> service.getCart(1L));
            try {
                assertThat(captured.await(5, TimeUnit.SECONDS)).isTrue();
                service.clearCart(1L);
            } finally {
                release.countDown();
            }
            // An overlapping read may finish with its old snapshot; later reads must be fresh.
            assertThat(pending.get(5, TimeUnit.SECONDS).totalPrice()).isEqualByComparingTo("10.00");
            assertThat(service.getCart(1L).totalPrice()).isEqualByComparingTo("0.00");
        }
    }
}
