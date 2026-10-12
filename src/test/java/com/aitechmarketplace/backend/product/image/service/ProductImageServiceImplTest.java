package com.aitechmarketplace.backend.product.image.service;

import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.image.entity.ProductImage;
import com.aitechmarketplace.backend.product.image.repository.ProductImageRepository;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.storage.service.S3StorageService;
import com.aitechmarketplace.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private S3StorageService storageService;

    private ProductImageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductImageServiceImpl(
                productRepository,
                productImageRepository,
                storageService
        );
    }

    @Test
    void upload_shouldUploadAndSaveImage() throws Exception {
        Product product = createProduct(10L);
        MultipartFile file = createImageFile();

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(productImageRepository.countByProductId(1L))
                .thenReturn(2);

        when(productImageRepository.save(any(ProductImage.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        ProductImage result = service.upload(
                1L,
                10L,
                false,
                file
        );

        assertNotNull(result);
        assertSame(product, result.getProduct());

        assertEquals(
                "test-image.jpg",
                result.getOriginalFilename()
        );

        assertEquals(
                "image/jpeg",
                result.getContentType()
        );

        assertEquals(
                5L,
                result.getFileSize()
        );

        assertEquals(
                2,
                result.getDisplayOrder()
        );

        assertNotNull(result.getObjectKey());

        assertTrue(
                result.getObjectKey()
                        .startsWith("products/1/")
        );

        assertTrue(
                result.getObjectKey()
                        .endsWith(".jpg")
        );

        verify(storageService).upload(
                eq(result.getObjectKey()),
                any(),
                eq(5L),
                eq("image/jpeg")
        );

        verify(productImageRepository)
                .save(any(ProductImage.class));
    }

    @Test
    void upload_shouldAllowAdminForAnotherSellerProduct()
            throws Exception {

        Product product = createProduct(10L);
        MultipartFile file = createImageFile();

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(productImageRepository.countByProductId(1L))
                .thenReturn(0);

        when(productImageRepository.save(any(ProductImage.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        ProductImage result = service.upload(
                1L,
                999L,
                true,
                file
        );

        assertNotNull(result);

        verify(storageService).upload(
                eq(result.getObjectKey()),
                any(),
                eq(5L),
                eq("image/jpeg")
        );

        verify(productImageRepository)
                .save(any(ProductImage.class));
    }

    @Test
    void upload_shouldRejectAnotherSeller() {

        Product product = createProduct(10L);
        MultipartFile file = createImageFile();

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        assertThrows(
                ForbiddenException.class,
                () -> service.upload(
                        1L,
                        999L,
                        false,
                        file
                )
        );

        verifyNoInteractions(storageService);

        verify(
                productImageRepository,
                never()
        ).save(any());
    }

    @Test
    void upload_shouldRejectMissingProduct() {

        MultipartFile file = createImageFile();

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.upload(
                        999L,
                        10L,
                        false,
                        file
                )
        );

        verifyNoInteractions(storageService);

        verify(
                productImageRepository,
                never()
        ).save(any());
    }

    @Test
    void upload_shouldRejectEmptyFile() {

        Product product = createProduct(10L);

        MultipartFile file = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.upload(
                                1L,
                                10L,
                                false,
                                file
                        )
                );

        assertEquals(
                "Image file is required",
                exception.getMessage()
        );

        verifyNoInteractions(storageService);

        verify(
                productImageRepository,
                never()
        ).save(any());
    }

    @Test
    void upload_shouldRejectNonImageFile() {

        Product product = createProduct(10L);

        MultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "hello".getBytes()
        );

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.upload(
                                1L,
                                10L,
                                false,
                                file
                        )
                );

        assertEquals(
                "Only image files are allowed",
                exception.getMessage()
        );

        verifyNoInteractions(storageService);

        verify(
                productImageRepository,
                never()
        ).save(any());
    }

    @Test
    void upload_shouldRejectFileWithNullContentType() {

        Product product = createProduct(10L);

        MultipartFile file = new MockMultipartFile(
                "file",
                "image.jpg",
                null,
                "hello".getBytes()
        );

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.upload(
                                1L,
                                10L,
                                false,
                                file
                        )
                );

        assertEquals(
                "Only image files are allowed",
                exception.getMessage()
        );

        verifyNoInteractions(storageService);

        verify(
                productImageRepository,
                never()
        ).save(any());
    }

    @Test
    void upload_shouldDeleteObjectWhenDatabaseSaveFails()
            throws Exception {

        Product product = createProduct(10L);
        MultipartFile file = createImageFile();

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(productImageRepository.countByProductId(1L))
                .thenReturn(0);

        when(productImageRepository.save(any(ProductImage.class)))
                .thenThrow(
                        new RuntimeException("Database error")
                );

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> service.upload(
                                1L,
                                10L,
                                false,
                                file
                        )
                );

        assertEquals(
                "Database error",
                exception.getMessage()
        );

        verify(storageService).upload(
                anyString(),
                any(),
                eq(5L),
                eq("image/jpeg")
        );

        verify(storageService).delete(
                anyString()
        );
    }

    @Test
    void upload_shouldPropagateStorageUploadException()
            throws Exception {

        Product product = createProduct(10L);
        MultipartFile file = createImageFile();

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        doThrow(
                new RuntimeException("Storage error")
        ).when(storageService).upload(
                anyString(),
                any(),
                eq(5L),
                eq("image/jpeg")
        );

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> service.upload(
                                1L,
                                10L,
                                false,
                                file
                        )
                );

        assertEquals(
                "Storage error",
                exception.getMessage()
        );

        verify(
                productImageRepository,
                never()
        ).save(any());

        verify(
                storageService,
                never()
        ).delete(anyString());
    }

    @Test
    void findByProductId_shouldReturnImages() {

        ProductImage image1 = new ProductImage();
        ProductImage image2 = new ProductImage();

        when(productRepository.existsById(1L))
                .thenReturn(true);

        when(
                productImageRepository
                        .findByProductIdOrderByDisplayOrderAscIdAsc(1L)
        ).thenReturn(
                List.of(image1, image2)
        );

        List<ProductImage> result =
                service.findByProductId(1L);

        assertEquals(
                2,
                result.size()
        );

        assertSame(
                image1,
                result.get(0)
        );

        assertSame(
                image2,
                result.get(1)
        );
    }

    @Test
    void findByProductId_shouldReturnEmptyListWhenNoImages() {

        when(productRepository.existsById(1L))
                .thenReturn(true);

        when(
                productImageRepository
                        .findByProductIdOrderByDisplayOrderAscIdAsc(1L)
        ).thenReturn(List.of());

        List<ProductImage> result =
                service.findByProductId(1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByProductId_shouldRejectMissingProduct() {

        when(productRepository.existsById(999L))
                .thenReturn(false);

        assertThrows(
                NotFoundException.class,
                () -> service.findByProductId(999L)
        );

        verify(
                productImageRepository,
                never()
        ).findByProductIdOrderByDisplayOrderAscIdAsc(
                anyLong()
        );
    }

    @Test
    void delete_shouldDeleteStorageObjectAndDatabaseRecord() {

        Product product = createProduct(10L);

        ProductImage image = new ProductImage();

        image.setProduct(product);

        image.setObjectKey(
                "products/1/test-image.jpg"
        );

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(
                productImageRepository
                        .findByIdAndProductId(100L, 1L)
        ).thenReturn(
                Optional.of(image)
        );

        service.delete(
                1L,
                100L,
                10L,
                false
        );

        verify(storageService)
                .delete(
                        "products/1/test-image.jpg"
                );

        verify(productImageRepository)
                .delete(image);
    }

    @Test
    void delete_shouldAllowAdminForAnotherSeller() {

        Product product = createProduct(10L);

        ProductImage image = new ProductImage();

        image.setProduct(product);

        image.setObjectKey(
                "products/1/test-image.jpg"
        );

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(
                productImageRepository
                        .findByIdAndProductId(100L, 1L)
        ).thenReturn(
                Optional.of(image)
        );

        service.delete(
                1L,
                100L,
                999L,
                true
        );

        verify(storageService)
                .delete(
                        "products/1/test-image.jpg"
                );

        verify(productImageRepository)
                .delete(image);
    }

    @Test
    void delete_shouldRejectAnotherSeller() {

        Product product = createProduct(10L);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        assertThrows(
                ForbiddenException.class,
                () -> service.delete(
                        1L,
                        100L,
                        999L,
                        false
                )
        );

        verifyNoInteractions(storageService);

        verify(
                productImageRepository,
                never()
        ).delete(any());
    }

    @Test
    void delete_shouldRejectMissingProduct() {

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.delete(
                        999L,
                        100L,
                        10L,
                        false
                )
        );

        verifyNoInteractions(storageService);

        verify(
                productImageRepository,
                never()
        ).delete(any());
    }

    @Test
    void delete_shouldRejectMissingImage() {

        Product product = createProduct(10L);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(
                productImageRepository
                        .findByIdAndProductId(999L, 1L)
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                NotFoundException.class,
                () -> service.delete(
                        1L,
                        999L,
                        10L,
                        false
                )
        );

        verifyNoInteractions(storageService);

        verify(
                productImageRepository,
                never()
        ).delete(any());
    }

    @Test
    void download_shouldReturnObjectFromStorage() {

        Product product = createProduct(10L);

        ProductImage image = new ProductImage();

        image.setProduct(product);

        image.setObjectKey(
                "products/1/test-image.jpg"
        );

        @SuppressWarnings("unchecked")
        ResponseBytes<GetObjectResponse> response =
                mock(ResponseBytes.class);

        when(productRepository.existsById(1L))
                .thenReturn(true);

        when(
                productImageRepository
                        .findByIdAndProductId(100L, 1L)
        ).thenReturn(
                Optional.of(image)
        );

        when(
                storageService.download(
                        "products/1/test-image.jpg"
                )
        ).thenReturn(response);

        ResponseBytes<GetObjectResponse> result =
                service.download(
                        1L,
                        100L
                );

        assertSame(
                response,
                result
        );

        verify(storageService)
                .download(
                        "products/1/test-image.jpg"
                );
    }

    @Test
    void download_shouldRejectMissingProduct() {

        when(productRepository.existsById(999L))
                .thenReturn(false);

        assertThrows(
                NotFoundException.class,
                () -> service.download(
                        999L,
                        100L
                )
        );

        verifyNoInteractions(storageService);

        verify(
                productImageRepository,
                never()
        ).findByIdAndProductId(
                anyLong(),
                anyLong()
        );
    }

    @Test
    void download_shouldRejectMissingImage() {

        when(productRepository.existsById(1L))
                .thenReturn(true);

        when(
                productImageRepository
                        .findByIdAndProductId(999L, 1L)
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                NotFoundException.class,
                () -> service.download(
                        1L,
                        999L
                )
        );

        verifyNoInteractions(storageService);
    }

    private MultipartFile createImageFile() {
        return new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                "hello".getBytes()
        );
    }

    private Product createProduct(Long sellerId) {
        Product product = new Product();

        User seller = new User();

        setEntityId(
                seller,
                sellerId
        );

        product.setSeller(seller);
        product.setName("Test Product");
        product.setPrice(
                BigDecimal.valueOf(100)
        );

        return product;
    }

    private void setEntityId(
            User entity,
            Long id
    ) {
        try {
            Field idField =
                    User.class.getDeclaredField("id");

            idField.setAccessible(true);
            idField.set(entity, id);

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Cannot set User id for test",
                    e
            );
        }
    }
}