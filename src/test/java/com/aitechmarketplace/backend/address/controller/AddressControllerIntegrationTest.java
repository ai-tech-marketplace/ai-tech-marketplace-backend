package com.aitechmarketplace.backend.address.controller;

import com.aitechmarketplace.backend.address.repository.AddressRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import com.aitechmarketplace.backend.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AddressControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanup() {
        addressRepository.deleteAll();
        createdUserIds.forEach(id -> {
            if (userRepository.existsById(id)) {
                userRepository.deleteById(id);
            }
        });
        createdUserIds.clear();
    }

    @Test
    void getAddresses_withoutToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }

    @Test
    void createAddress_shouldSucceedAndBeDefaultIfFirst() throws Exception {
        User user = createUser();
        String token = login(user.getEmail());

        mockMvc.perform(post("/api/v1/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "recipientName": "John Doe",
                                    "phone": "0901234567",
                                    "addressLine": "123 Example Street",
                                    "ward": "Dich Vong Hau",
                                    "district": "Cau Giay",
                                    "city": "Hanoi",
                                    "country": "Vietnam"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.recipientName", is("John Doe")))
                .andExpect(jsonPath("$.data.isDefault", is(true)));
    }

    @Test
    void createAddress_withInvalidPhone_shouldReturnBadRequest() throws Exception {
        User user = createUser();
        String token = login(user.getEmail());

        mockMvc.perform(post("/api/v1/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "recipientName": "John Doe",
                                    "phone": "invalid-phone",
                                    "addressLine": "123 Example Street",
                                    "city": "Hanoi"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("INVALID_REQUEST_PAYLOAD")));
    }

    @Test
    void addressLifecycle_createUpdateSetDefaultDelete_shouldWork() throws Exception {
        User user = createUser();
        String token = login(user.getEmail());

        // 1. Create first address (becomes default)
        String res1 = mockMvc.perform(post("/api/v1/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "recipientName": "Home Address",
                                    "phone": "0901111222",
                                    "addressLine": "Home street line",
                                    "city": "Hanoi"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault", is(true)))
                .andReturn().getResponse().getContentAsString();

        long addr1Id = objectMapper.readTree(res1).path("data").path("id").asLong();

        // 2. Create second address (not default)
        String res2 = mockMvc.perform(post("/api/v1/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "recipientName": "Office Address",
                                    "phone": "0903333444",
                                    "addressLine": "Office tower line",
                                    "city": "Hanoi",
                                    "isDefault": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault", is(false)))
                .andReturn().getResponse().getContentAsString();

        long addr2Id = objectMapper.readTree(res2).path("data").path("id").asLong();

        // 3. Set second address as default
        mockMvc.perform(patch("/api/v1/addresses/" + addr2Id + "/default")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault", is(true)));

        // Verify first address is no longer default
        mockMvc.perform(get("/api/v1/addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // 4. Update address 1
        mockMvc.perform(put("/api/v1/addresses/" + addr1Id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "recipientName": "Home Address Updated",
                                    "phone": "0901111222",
                                    "addressLine": "Updated street line",
                                    "city": "Hanoi"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientName", is("Home Address Updated")));

        // 5. Delete address 1
        mockMvc.perform(delete("/api/v1/addresses/" + addr1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify remaining count is 1
        mockMvc.perform(get("/api/v1/addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void accessOtherUserAddress_shouldReturnNotFound() throws Exception {
        User user1 = createUser();
        User user2 = createUser();
        String token1 = login(user1.getEmail());
        String token2 = login(user2.getEmail());

        String res = mockMvc.perform(post("/api/v1/addresses")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "recipientName": "User1 Address",
                                    "phone": "0901111222",
                                    "addressLine": "Street 1",
                                    "city": "Hanoi"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long addrId = objectMapper.readTree(res).path("data").path("id").asLong();

        // User 2 tries to update or delete User 1's address
        mockMvc.perform(delete("/api/v1/addresses/" + addrId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("ADDRESS_NOT_FOUND")));
    }

    private User createUser() {
        String email = "address-test-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Address Tester", null);
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
