package com.aitechmarketplace.backend.product.image.controller;

import com.aitechmarketplace.backend.product.image.entity.ProductImage;
import com.aitechmarketplace.backend.product.image.repository.ProductImageRepository;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.storage.service.S3StorageService;
import com.aitechmarketplace.backend.user.entity.Role;
import com.aitechmarketplace.backend.user.entity.RoleName;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.RoleRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductImageControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";

    private static final byte[] IMAGE_CONTENT =
            "fake-jpeg-content".getBytes();

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private S3StorageService storageService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> createdUserIds =
            new ArrayList<>();

    private final List<Long> createdProductIds =
            new ArrayList<>();

    @AfterEach
    void cleanup() {

        /*
         * Remove objects from S3-compatible storage first.
         * Database cascade alone would not remove the objects
         * from SeaweedFS.
         */
        createdProductIds.forEach(productId -> {

            productImageRepository
                    .findByProductIdOrderByDisplayOrderAscIdAsc(
                            productId
                    )
                    .forEach(image -> {

                        try {
                            storageService.delete(
                                    image.getObjectKey()
                            );
                        } catch (Exception ignored) {
                            // Object may already have been deleted.
                        }
                    });
        });

        /*
         * Deleting the product cascades to product_images
         * because V5 uses ON DELETE CASCADE.
         */
        createdProductIds.forEach(id ->
                productRepository.findById(id)
                        .ifPresent(productRepository::delete)
        );

        createdUserIds.forEach(id -> {
            if (userRepository.existsById(id)) {
                userRepository.deleteById(id);
            }
        });
    }

    @Test
    void upload_shouldCreateProductImage()
            throws Exception {

        User seller = createUser();

        String token = login(
                seller.getEmail()
        );

        long productId = createProduct(
                token,
                "Image Product"
        );

        MockMultipartFile file =
                createImageFile(
                        "product-image.jpg"
                );

        String response = mockMvc.perform(
                multipart(
                        "/api/products/{productId}/images",
                        productId
                )
                        .file(file)
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath(
                                "$.productId",
                                is((int) productId)
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.originalFilename",
                                is("product-image.jpg")
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.contentType",
                                is("image/jpeg")
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.fileSize",
                                is(17)
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.displayOrder",
                                is(0)
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.objectKey",
                                startsWith(
                                        "products/" +
                                        productId +
                                        "/"
                                )
                        )
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json =
                objectMapper.readTree(response);

        long imageId =
                json.get("id").asLong();

        assert imageId > 0;

        ProductImage image =
                productImageRepository
                        .findById(imageId)
                        .orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals(
                productId,
                image.getProduct().getId()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "product-image.jpg",
                image.getOriginalFilename()
        );
    }

    @Test
    void upload_shouldAssignIncreasingDisplayOrder()
            throws Exception {

        User seller = createUser();

        String token = login(
                seller.getEmail()
        );

        long productId = createProduct(
                token,
                "Multiple Images Product"
        );

        String firstResponse =
                uploadImage(
                        token,
                        productId,
                        "first.jpg"
                );

        String secondResponse =
                uploadImage(
                        token,
                        productId,
                        "second.jpg"
                );

        JsonNode firstJson =
                objectMapper.readTree(firstResponse);

        JsonNode secondJson =
                objectMapper.readTree(secondResponse);

        org.junit.jupiter.api.Assertions.assertEquals(
                0,
                firstJson.get("displayOrder").asInt()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                secondJson.get("displayOrder").asInt()
        );
    }

    @Test
    void uploadByAnotherUser_shouldReturnForbidden()
            throws Exception {

        User owner = createUser();

        User anotherUser = createUser();

        String ownerToken =
                login(owner.getEmail());

        String anotherUserToken =
                login(anotherUser.getEmail());

        long productId = createProduct(
                ownerToken,
                "Owner Product"
        );

        MockMultipartFile file =
                createImageFile(
                        "forbidden.jpg"
                );

        mockMvc.perform(
                multipart(
                        "/api/products/{productId}/images",
                        productId
                )
                        .file(file)
                        .header(
                                "Authorization",
                                "Bearer " +
                                anotherUserToken
                        )
        )
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUploadImageForAnotherUserProduct()
            throws Exception {

        User owner = createUser();

        User admin = createAdmin();

        String ownerToken =
                login(owner.getEmail());

        String adminToken =
                login(admin.getEmail());

        long productId = createProduct(
                ownerToken,
                "Admin Image Product"
        );

        MockMultipartFile file =
                createImageFile(
                        "admin-upload.jpg"
                );

        mockMvc.perform(
                multipart(
                        "/api/products/{productId}/images",
                        productId
                )
                        .file(file)
                        .header(
                                "Authorization",
                                "Bearer " + adminToken
                        )
        )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath(
                                "$.productId",
                                is((int) productId)
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.originalFilename",
                                is("admin-upload.jpg")
                        )
                );
    }

    @Test
    void uploadWithoutToken_shouldReturnUnauthorized()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        long productId = createProduct(
                token,
                "Unauthorized Upload Product"
        );

        MockMultipartFile file =
                createImageFile(
                        "unauthorized.jpg"
                );

        mockMvc.perform(
                multipart(
                        "/api/products/{productId}/images",
                        productId
                )
                        .file(file)
        )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadEmptyFile_shouldReturnBadRequest()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        long productId = createProduct(
                token,
                "Empty Image Product"
        );

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "empty.jpg",
                        "image/jpeg",
                        new byte[0]
                );

        mockMvc.perform(
                multipart(
                        "/api/products/{productId}/images",
                        productId
                )
                        .file(file)
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadNonImageFile_shouldReturnBadRequest()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        long productId = createProduct(
                token,
                "Invalid Image Product"
        );

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "document.pdf",
                        "application/pdf",
                        "pdf-content".getBytes()
                );

        mockMvc.perform(
                multipart(
                        "/api/products/{productId}/images",
                        productId
                )
                        .file(file)
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFileWithNullContentType_shouldReturnBadRequest()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        long productId = createProduct(
                token,
                "Null Content Type Product"
        );

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "unknown.jpg",
                        null,
                        IMAGE_CONTENT
                );

        mockMvc.perform(
                multipart(
                        "/api/products/{productId}/images",
                        productId
                )
                        .file(file)
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadForMissingProduct_shouldReturnNotFound()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        MockMultipartFile file =
                createImageFile(
                        "missing-product.jpg"
                );

        mockMvc.perform(
                multipart(
                        "/api/products/{productId}/images",
                        999999999L
                )
                        .file(file)
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isNotFound());
    }

    @Test
    void findByProductId_shouldReturnUploadedImages()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        long productId = createProduct(
                token,
                "List Images Product"
        );

        uploadImage(
                token,
                productId,
                "first.jpg"
        );

        uploadImage(
                token,
                productId,
                "second.jpg"
        );

        mockMvc.perform(
                get(
                        "/api/products/{productId}/images",
                        productId
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$",
                                hasSize(2)
                        )
                )
                .andExpect(
                        jsonPath(
                                "$[0].originalFilename",
                                is("first.jpg")
                        )
                )
                .andExpect(
                        jsonPath(
                                "$[0].displayOrder",
                                is(0)
                        )
                )
                .andExpect(
                        jsonPath(
                                "$[1].originalFilename",
                                is("second.jpg")
                        )
                )
                .andExpect(
                        jsonPath(
                                "$[1].displayOrder",
                                is(1)
                        )
                );
    }

    @Test
    void findByProductId_whenProductDoesNotExist_shouldReturnNotFound()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        mockMvc.perform(
                get(
                        "/api/products/{productId}/images",
                        999999999L
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isNotFound());
    }

    @Test
    void download_shouldReturnUploadedImageContent()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        long productId = createProduct(
                token,
                "Download Image Product"
        );

        String uploadResponse =
                uploadImage(
                        token,
                        productId,
                        "download.jpg"
                );

        JsonNode uploadJson =
                objectMapper.readTree(
                        uploadResponse
                );

        long imageId =
                uploadJson.get("id").asLong();

        mockMvc.perform(
                get(
                        "/api/products/{productId}/images/{imageId}/content",
                        productId,
                        imageId
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentType(
                                MediaType.IMAGE_JPEG
                        )
                )
                .andExpect(
                        content().bytes(
                                IMAGE_CONTENT
                        )
                );
    }

    @Test
    void download_whenImageDoesNotExist_shouldReturnNotFound()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        long productId = createProduct(
                token,
                "Missing Image Product"
        );

        mockMvc.perform(
                get(
                        "/api/products/{productId}/images/{imageId}/content",
                        productId,
                        999999999L
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isNotFound());
    }

    @Test
    void download_whenProductDoesNotExist_shouldReturnNotFound()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        mockMvc.perform(
                get(
                        "/api/products/{productId}/images/{imageId}/content",
                        999999999L,
                        999999999L
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_shouldRemoveImage()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        long productId = createProduct(
                token,
                "Delete Image Product"
        );

        String uploadResponse =
                uploadImage(
                        token,
                        productId,
                        "delete.jpg"
                );

        JsonNode uploadJson =
                objectMapper.readTree(
                        uploadResponse
                );

        long imageId =
                uploadJson.get("id").asLong();

        mockMvc.perform(
                delete(
                        "/api/products/{productId}/images/{imageId}",
                        productId,
                        imageId
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                get(
                        "/api/products/{productId}/images",
                        productId
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$",
                                hasSize(0)
                        )
                );

        mockMvc.perform(
                get(
                        "/api/products/{productId}/images/{imageId}/content",
                        productId,
                        imageId
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteByAnotherUser_shouldReturnForbidden()
            throws Exception {

        User owner = createUser();

        User anotherUser = createUser();

        String ownerToken =
                login(owner.getEmail());

        String anotherUserToken =
                login(anotherUser.getEmail());

        long productId = createProduct(
                ownerToken,
                "Delete Forbidden Product"
        );

        String uploadResponse =
                uploadImage(
                        ownerToken,
                        productId,
                        "protected.jpg"
                );

        JsonNode uploadJson =
                objectMapper.readTree(
                        uploadResponse
                );

        long imageId =
                uploadJson.get("id").asLong();

        mockMvc.perform(
                delete(
                        "/api/products/{productId}/images/{imageId}",
                        productId,
                        imageId
                )
                        .header(
                                "Authorization",
                                "Bearer " +
                                anotherUserToken
                        )
        )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                get(
                        "/api/products/{productId}/images",
                        productId
                )
                        .header(
                                "Authorization",
                                "Bearer " +
                                ownerToken
                        )
        )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$",
                                hasSize(1)
                        )
                );
    }

    @Test
    void adminCanDeleteImageForAnotherUserProduct()
            throws Exception {

        User owner = createUser();

        User admin = createAdmin();

        String ownerToken =
                login(owner.getEmail());

        String adminToken =
                login(admin.getEmail());

        long productId = createProduct(
                ownerToken,
                "Admin Delete Product"
        );

        String uploadResponse =
                uploadImage(
                        ownerToken,
                        productId,
                        "admin-delete.jpg"
                );

        JsonNode uploadJson =
                objectMapper.readTree(
                        uploadResponse
                );

        long imageId =
                uploadJson.get("id").asLong();

        mockMvc.perform(
                delete(
                        "/api/products/{productId}/images/{imageId}",
                        productId,
                        imageId
                )
                        .header(
                                "Authorization",
                                "Bearer " + adminToken
                        )
        )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                get(
                        "/api/products/{productId}/images",
                        productId
                )
                        .header(
                                "Authorization",
                                "Bearer " + ownerToken
                        )
        )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$",
                                hasSize(0)
                        )
                );
    }

    @Test
    void delete_whenImageDoesNotExist_shouldReturnNotFound()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        long productId = createProduct(
                token,
                "Missing Delete Image Product"
        );

        mockMvc.perform(
                delete(
                        "/api/products/{productId}/images/{imageId}",
                        productId,
                        999999999L
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_whenProductDoesNotExist_shouldReturnNotFound()
            throws Exception {

        User seller = createUser();

        String token =
                login(seller.getEmail());

        mockMvc.perform(
                delete(
                        "/api/products/{productId}/images/{imageId}",
                        999999999L,
                        999999999L
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isNotFound());
    }

    private String uploadImage(
            String token,
            long productId,
            String filename
    ) throws Exception {

        MockMultipartFile file =
                createImageFile(filename);

        return mockMvc.perform(
                multipart(
                        "/api/products/{productId}/images",
                        productId
                )
                        .file(file)
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private MockMultipartFile createImageFile(
            String filename
    ) {
        return new MockMultipartFile(
                "file",
                filename,
                "image/jpeg",
                IMAGE_CONTENT
        );
    }

    private long createProduct(
            String token,
            String name
    ) throws Exception {

        String response = mockMvc.perform(
                org.springframework.test.web.servlet
                        .request.MockMvcRequestBuilders
                        .post("/api/products")
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content("""
                                {
                                    "name": "%s",
                                    "description": "Test product",
                                    "price": 1000.00,
                                    "stockQuantity": 10
                                }
                                """.formatted(name))
        )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long productId =
                objectMapper
                        .readTree(response)
                        .get("id")
                        .asLong();

        createdProductIds.add(productId);

        return productId;
    }

    private User createUser() {

        String email =
                "product-image-test-" +
                UUID.randomUUID() +
                "@example.com";

        String passwordHash =
                passwordEncoder.encode(PASSWORD);

        User user =
                userService.register(
                        email,
                        passwordHash,
                        "Image",
                        "Tester"
                );

        createdUserIds.add(user.getId());

        return user;
    }

    private User createAdmin() {

        String email =
                "product-image-admin-" +
                UUID.randomUUID() +
                "@example.com";

        String passwordHash =
                passwordEncoder.encode(PASSWORD);

        User admin =
                userService.register(
                        email,
                        passwordHash,
                        "Image",
                        "Admin"
                );

        Role adminRole =
                roleRepository
                        .findByName(RoleName.ADMIN)
                        .orElseThrow();

        admin.getRoles().add(adminRole);

        admin =
                userRepository.save(admin);

        createdUserIds.add(admin.getId());

        return admin;
    }

    private String login(String email)
            throws Exception {

        String response =
                mockMvc.perform(
                        org.springframework.test.web.servlet
                                .request.MockMvcRequestBuilders
                                .post("/api/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                            "email": "%s",
                                            "password": "%s"
                                        }
                                        """.formatted(
                                                email,
                                                PASSWORD
                                        ))
                )
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode json =
                objectMapper.readTree(response);

        return json.get("token").asText();
    }
}

