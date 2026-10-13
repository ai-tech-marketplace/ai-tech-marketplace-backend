package com.aitechmarketplace.backend.auth.controller;

import com.aitechmarketplace.backend.user.repository.UserRepository;
import com.aitechmarketplace.backend.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    private static final String EMAIL = "auth-integration@example.com";
    private static final String PASSWORD = "12345678";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(EMAIL)
            .ifPresent(userRepository::delete);

        userRepository.findByEmail("newuser@example.com")
            .ifPresent(userRepository::delete);

        userService.register(
            EMAIL,
            passwordEncoder.encode(PASSWORD),
            "Integration Test",
            "0901234567"
        );
    }

    @Test
    void register_shouldCreateUser() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "newuser@example.com",
                            "password": "Password123!",
                            "fullName": "New User",
                            "phone": "0987654321"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.email", is("newuser@example.com")))
            .andExpect(jsonPath("$.data.fullName", is("New User")))
            .andExpect(jsonPath("$.data.role", is("CUSTOMER")));
    }

    @Test
    void login_shouldReturnJwtToken() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "auth-integration@example.com",
                            "password": "12345678"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
            .andExpect(jsonPath("$.data.user.email", is(EMAIL)))
            .andExpect(jsonPath("$.data.user.fullName", is("Integration Test")))
            .andExpect(jsonPath("$.data.user.role", is("CUSTOMER")));
    }

    @Test
    void login_withWrongPassword_shouldBeRejected() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "auth-integration@example.com",
                            "password": "wrong-password"
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.errorCode", is("AUTH_INVALID_CREDENTIALS")));
    }

    @Test
    void me_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }

    @Test
    void me_withValidToken_shouldReturnCurrentUser() throws Exception {
        MvcResult loginResult = mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "auth-integration@example.com",
                            "password": "12345678"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        String token = JsonPath.read(loginResponse, "$.data.accessToken");

        mockMvc.perform(
                get("/api/v1/auth/me")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.email", is(EMAIL)))
            .andExpect(jsonPath("$.data.fullName", is("Integration Test")))
            .andExpect(jsonPath("$.data.role", is("CUSTOMER")));
    }

    @Test
    void me_withInvalidToken_shouldBeRejected() throws Exception {
        mockMvc.perform(
                get("/api/v1/auth/me")
                    .header("Authorization", "Bearer invalid-token")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }
}