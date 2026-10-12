package com.aitechmarketplace.backend.product.image.dto;

import com.aitechmarketplace.backend.product.image.entity.ProductImage;

import java.time.OffsetDateTime;

public record ProductImageResponse(
        Long id,
        Long productId,
        String originalFilename,
        String contentType,
        Long fileSize,
        Integer displayOrder,
        String objectKey,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getProduct().getId(),
                image.getOriginalFilename(),
                image.getContentType(),
                image.getFileSize(),
                image.getDisplayOrder(),
                image.getObjectKey(),
                image.getCreatedAt(),
                image.getUpdatedAt()
        );
    }
}
