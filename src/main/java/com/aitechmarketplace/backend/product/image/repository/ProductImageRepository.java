package com.aitechmarketplace.backend.product.image.repository;

import com.aitechmarketplace.backend.product.image.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository
        extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByDisplayOrderAscIdAsc(
            Long productId
    );

    Optional<ProductImage> findByIdAndProductId(
            Long id,
            Long productId
    );

    int countByProductId(Long productId);
}
