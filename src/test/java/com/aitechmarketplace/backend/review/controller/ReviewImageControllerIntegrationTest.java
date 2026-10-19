package com.aitechmarketplace.backend.review.controller;

import com.aitechmarketplace.backend.storage.service.S3StorageService;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import com.aitechmarketplace.backend.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewImageControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private S3StorageService storageService;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdUserIds.forEach(id -> {
            if (userRepository.existsById(id)) userRepository.deleteById(id);
        });
        createdUserIds.clear();
    }

    @Test
    void uploadImage_unauthorized_shouldReturn401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/reviews/images").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadImage_authenticated_success() throws Exception {
        User user = createCustomerUser();
        String token = login(user.getEmail());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "unbox.png",
                "image/png",
                "image-binary-data".getBytes()
        );

        doNothing().when(storageService).upload(anyString(), any(), anyLong(), anyString());

        mockMvc.perform(
                multipart("/api/v1/reviews/images")
                        .file(file)
                        .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.imageUrl", startsWith("http://localhost:8333/products/reviews/")))
                .andExpect(jsonPath("$.message", is("Image uploaded successfully")));
    }

    private User createCustomerUser() {
        String email = "review-user-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Reviewer " + UUID.randomUUID(), null);
        createdUserIds.add(user.getId());
        return user;
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(email, PASSWORD))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.path("data").path("accessToken").asText();
    }
}
