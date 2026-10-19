package com.aitechmarketplace.backend.review.service;

import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.review.dto.ReviewImageUploadResponse;
import com.aitechmarketplace.backend.storage.service.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewImageServiceImplTest {

    @Mock
    private S3StorageService storageService;

    private ReviewImageServiceImpl reviewImageService;

    @BeforeEach
    void setUp() {
        reviewImageService = new ReviewImageServiceImpl(storageService, "http://localhost:8333", "products");
    }

    @Test
    void shouldUploadValidImageSuccessfully() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "review.webp",
                "image/webp",
                "test-image-content".getBytes()
        );

        doNothing().when(storageService).upload(anyString(), any(), anyLong(), anyString());

        ReviewImageUploadResponse response = reviewImageService.upload(file);

        assertThat(response).isNotNull();
        assertThat(response.imageUrl()).startsWith("http://localhost:8333/products/reviews/");
        assertThat(response.imageUrl()).endsWith(".webp");
        verify(storageService).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> reviewImageService.upload(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File must not be empty");
    }

    @Test
    void shouldRejectOversizedFile() {
        byte[] largeBytes = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", largeBytes);

        assertThatThrownBy(() -> reviewImageService.upload(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceed 5MB");
    }

    @Test
    void shouldRejectInvalidContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "pdf-data".getBytes());

        assertThatThrownBy(() -> reviewImageService.upload(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only JPEG, PNG and WEBP");
    }
}
