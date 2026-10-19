package com.aitechmarketplace.backend.review.service;

import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.review.dto.ReviewImageUploadResponse;
import com.aitechmarketplace.backend.storage.service.S3StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewImageServiceImpl implements ReviewImageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final S3StorageService storageService;
    private final String endpoint;
    private final String bucket;

    public ReviewImageServiceImpl(
            S3StorageService storageService,
            @Value("${storage.s3.endpoint:http://localhost:8333}") String endpoint,
            @Value("${storage.s3.bucket:products}") String bucket
    ) {
        this.storageService = storageService;
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.bucket = bucket;
    }

    @Override
    public ReviewImageUploadResponse upload(MultipartFile file) throws IOException {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long fileSize = file.getSize();
        String extension = extractExtension(originalFilename);

        String objectKey = "reviews/" + UUID.randomUUID() + extension;

        storageService.upload(objectKey, file.getInputStream(), fileSize, contentType);

        String imageUrl = String.format("%s/%s/%s", endpoint, bucket, objectKey);
        return new ReviewImageUploadResponse(imageUrl);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size must not exceed 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Only JPEG, PNG and WEBP images are allowed");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
