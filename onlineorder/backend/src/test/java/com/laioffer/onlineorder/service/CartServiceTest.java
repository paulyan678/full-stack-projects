package com.laioffer.onlineorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.laioffer.onlineorder.entity.CartEntity;
import com.laioffer.onlineorder.entity.MenuItemEntity;
import com.laioffer.onlineorder.entity.OrderItemEntity;
import com.laioffer.onlineorder.exception.ResourceNotFoundException;
import com.laioffer.onlineorder.model.CartDto;
import com.laioffer.onlineorder.repository.CartRepository;
import com.laioffer.onlineorder.repository.MenuItemRepository;
import com.laioffer.onlineorder.repository.OrderItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private OrderItemRepository orderItemRepository;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, menuItemRepository, orderItemRepository);
    }

    @Test
    void addMenuItemCreatesAnOrderItemAndUpdatesTotal() {
        CartEntity cart = new CartEntity(3L, 1L, new BigDecimal("4.00"));
        MenuItemEntity menuItem = menuItem(2L, "10.50");
        when(cartRepository.findLockedByCustomerId(1L)).thenReturn(Optional.of(cart));
        when(menuItemRepository.findById(2L)).thenReturn(Optional.of(menuItem));
        when(orderItemRepository.findByCartIdAndMenuItemId(3L, 2L)).thenReturn(Optional.empty());

        cartService.addMenuItemToCart(1L, 2L);

        verify(orderItemRepository).save(
                new OrderItemEntity(null, 2L, 3L, new BigDecimal("10.50"), 1));
        verify(cartRepository).updateTotalPrice(3L, new BigDecimal("14.50"));
    }

    @Test
    void addMenuItemIncrementsAnExistingOrderItem() {
        CartEntity cart = new CartEntity(3L, 1L, new BigDecimal("10.50"));
        MenuItemEntity menuItem = menuItem(2L, "10.50");
        OrderItemEntity existing = new OrderItemEntity(
                7L, 2L, 3L, new BigDecimal("10.50"), 1);
        when(cartRepository.findLockedByCustomerId(1L)).thenReturn(Optional.of(cart));
        when(menuItemRepository.findById(2L)).thenReturn(Optional.of(menuItem));
        when(orderItemRepository.findByCartIdAndMenuItemId(3L, 2L))
                .thenReturn(Optional.of(existing));

        cartService.addMenuItemToCart(1L, 2L);

        verify(orderItemRepository).save(
                new OrderItemEntity(7L, 2L, 3L, new BigDecimal("10.50"), 2));
        verify(cartRepository).updateTotalPrice(3L, new BigDecimal("21.00"));
    }

    @Test
    void addMenuItemRejectsAnUnknownMenuItem() {
        when(cartRepository.findLockedByCustomerId(1L)).thenReturn(Optional.of(
                new CartEntity(3L, 1L, BigDecimal.ZERO)));
        when(menuItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addMenuItemToCart(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getCartMapsItemsAndCalculatesLineTotals() {
        CartEntity cart = new CartEntity(3L, 1L, new BigDecimal("21.00"));
        OrderItemEntity orderItem = new OrderItemEntity(
                7L, 2L, 3L, new BigDecimal("10.50"), 2);
        MenuItemEntity menuItem = menuItem(2L, "10.50");
        when(cartRepository.findByCustomerId(1L)).thenReturn(Optional.of(cart));
        when(orderItemRepository.findAllByCartIdOrderByIdAsc(3L))
                .thenReturn(List.of(orderItem));
        when(menuItemRepository.findAllById(List.of(2L))).thenReturn(List.of(menuItem));

        CartDto result = cartService.getCart(1L);

        assertThat(result.totalPrice()).isEqualByComparingTo("21.00");
        assertThat(result.orderItems()).singleElement().satisfies(item -> {
            assertThat(item.menuItemName()).isEqualTo("Dish");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.lineTotal()).isEqualByComparingTo("21.00");
        });
    }

    @Test
    void clearCartDeletesItemsAndResetsTotal() {
        when(cartRepository.findLockedByCustomerId(1L)).thenReturn(Optional.of(
                new CartEntity(3L, 1L, new BigDecimal("12.00"))));

        cartService.clearCart(1L);

        verify(orderItemRepository).deleteByCartId(3L);
        verify(cartRepository).updateTotalPrice(3L, BigDecimal.ZERO);
    }

    private MenuItemEntity menuItem(Long id, String price) {
        return new MenuItemEntity(
                id, 4L, "Dish", "Description", new BigDecimal(price), "image.jpg");
    }
}
