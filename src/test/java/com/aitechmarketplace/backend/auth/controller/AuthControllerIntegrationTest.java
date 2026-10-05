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

    private static final String EMAIL =
        "auth-integration@example.com";

    private static final String PASSWORD =
        "12345678";

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

        userService.register(
            EMAIL,
            passwordEncoder.encode(PASSWORD),
            "Integration",
            "Test"
        );
    }

    @Test
    void login_shouldReturnJwtToken() throws Exception {
        mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "auth-integration@example.com",
                            "password": "12345678"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.tokenType", is("Bearer")))
            .andExpect(jsonPath("$.email", is(EMAIL)))
            .andExpect(jsonPath("$.roles[0]", is("USER")));
    }

   @Test
    void login_withWrongPassword_shouldBeRejected()
        throws Exception {

        mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "auth-integration@example.com",
                            "password": "wrong-password"
                        }
                        """)
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withoutToken_shouldBeRejected()
        throws Exception {

        mockMvc.perform(
                get("/api/users/me")
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidToken_shouldReturnCurrentUser()
        throws Exception {

        MvcResult loginResult = mockMvc.perform(
                post("/api/auth/login")
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

        String loginResponse =
            loginResult.getResponse().getContentAsString();

        String token = JsonPath.read(
            loginResponse,
            "$.token"
        );

        mockMvc.perform(
                get("/api/users/me")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.email", is(EMAIL))
            )
            .andExpect(
                jsonPath("$.firstName", is("Integration"))
            )
            .andExpect(
                jsonPath("$.lastName", is("Test"))
            )
            .andExpect(
                jsonPath("$.active", is(true))
            )
            .andExpect(
                jsonPath("$.roles[0]", is("USER"))
            );
    }

    @Test
    void me_withInvalidToken_shouldBeRejected()
        throws Exception {

        mockMvc.perform(
                get("/api/users/me")
                    .header(
                        "Authorization",
                        "Bearer invalid.jwt.token"
                    )
            )
            .andExpect(status().isUnauthorized());
    }
}