package com.laioffer.onlineorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.laioffer.onlineorder.entity.CartEntity;
import com.laioffer.onlineorder.entity.CustomerEntity;
import com.laioffer.onlineorder.exception.EmailAlreadyRegisteredException;
import com.laioffer.onlineorder.repository.CartRepository;
import com.laioffer.onlineorder.repository.CustomerRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserDetailsManager userDetailsManager;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(
                cartRepository, customerRepository, passwordEncoder, userDetailsManager);
    }

    @Test
    void signUpNormalizesEmailHashesPasswordAndCreatesCart() {
        when(userDetailsManager.userExists("person@example.com")).thenReturn(false);
        when(passwordEncoder.encode("safe-password")).thenReturn("{bcrypt}hash");
        when(customerRepository.updateNameByEmail("person@example.com", "Pat", "Lee"))
                .thenReturn(1);
        when(customerRepository.findByEmail("person@example.com")).thenReturn(Optional.of(
                new CustomerEntity(9L, "person@example.com", "{bcrypt}hash", true, "Pat", "Lee")));

        customerService.signUp(
                " Person@Example.COM ", "safe-password", " Pat ", " Lee ");

        ArgumentCaptor<UserDetails> userCaptor = ArgumentCaptor.forClass(UserDetails.class);
        verify(userDetailsManager).createUser(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("person@example.com");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("{bcrypt}hash");
        assertThat(userCaptor.getValue().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        verify(cartRepository).save(any(CartEntity.class));
    }

    @Test
    void signUpRejectsAnExistingEmail() {
        when(userDetailsManager.userExists("person@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.signUp(
                "person@example.com", "safe-password", "Pat", "Lee"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userDetailsManager, never()).createUser(any());
    }
}
