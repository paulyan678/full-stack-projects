package com.laioffer.onlineorder.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laioffer.onlineorder.entity.MenuItemEntity;
import com.laioffer.onlineorder.repository.MenuItemRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
class OnlineOrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void restaurantMenusArePublicAndSeeded() throws Exception {
        mockMvc.perform(get("/restaurants/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].menu_items").isArray());
    }

    @Test
    void cartRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupRequiresCsrfAndValidInput() throws Exception {
        String validBody = """
                {
                  "email": "csrf@example.com",
                  "password": "safe-password",
                  "first_name": "Casey",
                  "last_name": "Smith"
                }
                """;

        mockMvc.perform(post("/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());

        CsrfCredentials validationCsrf = csrfCredentials();
        mockMvc.perform(withCsrf(post("/signup"), validationCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "short",
                                  "first_name": "",
                                  "last_name": "Smith"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.first_name").exists());
    }

    @Test
    void csrfEndpointIssuesARealCookieAndHeaderTokenForBrowserRequests() throws Exception {
        CsrfCredentials csrf = csrfCredentials();

        assertTrue(csrf.cookie().isHttpOnly());
        assertFalse(csrf.cookie().getSecure());
        assertEquals("Lax", csrf.cookie().getAttribute("SameSite"));

        mockMvc.perform(withCsrf(post("/signup"), csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("browser@example.com", "safe-password")))
                .andExpect(status().isCreated());
    }

    @Test
    void userCanRegisterLoginAddToCartAndCheckout() throws Exception {
        String email = "flow@example.com";
        String password = "safe-password";
        register(email, password);

        CsrfCredentials loginCsrf = csrfCredentials();
        MvcResult loginResult = mockMvc.perform(withCsrf(post("/login"), loginCsrf)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", " FLOW@EXAMPLE.COM ")
                        .param("password", password))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(get("/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_price").value(0))
                .andExpect(jsonPath("$.order_items").isEmpty());

        MenuItemEntity menuItem = menuItemRepository.findAll().getFirst();
        CsrfCredentials cartCsrf = csrfCredentials(session);
        mockMvc.perform(withCsrf(post("/cart").session(session), cartCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menu_id\":" + menuItem.id() + "}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_price").value(menuItem.price().doubleValue()))
                .andExpect(jsonPath("$.order_items[0].menu_item_id").value(menuItem.id()))
                .andExpect(jsonPath("$.order_items[0].quantity").value(1));

        mockMvc.perform(withCsrf(post("/cart/checkout").session(session), cartCsrf))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_price").value(0))
                .andExpect(jsonPath("$.order_items").isEmpty());
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        register("duplicate@example.com", "safe-password");

        mockMvc.perform(withCsrf(post("/signup"), csrfCredentials())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("DUPLICATE@example.com", "safe-password")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Account already exists"));
    }

    @Test
    void cartsAreIsolatedByAuthenticatedCustomer() throws Exception {
        register("first@example.com", "safe-password");
        register("second@example.com", "safe-password");
        MockHttpSession firstSession = login("first@example.com", "safe-password");
        MockHttpSession secondSession = login("second@example.com", "safe-password");
        MenuItemEntity menuItem = menuItemRepository.findAll().getFirst();

        mockMvc.perform(withCsrf(post("/cart").session(firstSession), csrfCredentials(firstSession))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menu_id\":" + menuItem.id() + "}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/cart").session(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_items.length()").value(1));
        mockMvc.perform(get("/cart").session(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_items").isEmpty());
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(withCsrf(post("/signup"), csrfCredentials())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(email, password)))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        CsrfCredentials csrf = csrfCredentials();
        MvcResult result = mockMvc.perform(withCsrf(post("/login"), csrf)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private CsrfCredentials csrfCredentials() throws Exception {
        return csrfCredentials(null);
    }

    private CsrfCredentials csrfCredentials(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = get("/auth/csrf");
        if (session != null) {
            request.session(session);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.header_name").value("X-XSRF-TOKEN"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        return new CsrfCredentials(
                cookie, body.get("header_name").asText(), body.get("token").asText());
    }

    private MockHttpServletRequestBuilder withCsrf(
            MockHttpServletRequestBuilder request, CsrfCredentials csrf) {
        return request.cookie(csrf.cookie()).header(csrf.headerName(), csrf.token());
    }

    private String registrationJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "first_name": "Taylor",
                  "last_name": "Jordan"
                }
                """.formatted(email, password);
    }

    private record CsrfCredentials(Cookie cookie, String headerName, String token) {
    }
}
