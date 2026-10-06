package com.aitechmarketplace.backend.product.image.service;

import com.aitechmarketplace.backend.product.image.entity.ProductImage;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ProductImageService {

    ProductImage upload(
            Long productId,
            Long userId,
            boolean isAdmin,
            MultipartFile file
    ) throws IOException;

    List<ProductImage> findByProductId(Long productId);

    void delete(
            Long productId,
            Long imageId,
            Long userId,
            boolean isAdmin
    );

    ResponseBytes<GetObjectResponse> download(
        Long productId,
        Long imageId
);
}
