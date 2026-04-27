package com.laioffer.onlineorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.laioffer.onlineorder.entity.RestaurantEntity;
import com.laioffer.onlineorder.repository.MenuItemRepository;
import com.laioffer.onlineorder.repository.RestaurantRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @InjectMocks
    private RestaurantService restaurantService;

    @Test
    void restaurantsWithoutMenuItemsHaveAnEmptyList() {
        when(menuItemRepository.findAll()).thenReturn(List.of());
        when(restaurantRepository.findAll()).thenReturn(List.of(
                new RestaurantEntity(1L, "Cafe", "Main Street", "555-0100", null)));

        assertThat(restaurantService.getRestaurants())
                .singleElement()
                .satisfies(restaurant -> assertThat(restaurant.menuItems()).isEmpty());
    }
}
