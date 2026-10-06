package com.aitechmarketplace.backend.product.image.service;

import com.aitechmarketplace.backend.product.image.entity.ProductImage;
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
}
