package com.aitechmarketplace.backend.product.service;

import com.aitechmarketplace.backend.product.dto.*;
import com.aitechmarketplace.backend.product.entity.Product;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductService {

    Product create(
            Long sellerId,
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            List<Long> categoryIds
    );

    Product create(Long sellerId, ProductCreateRequest request);

    Optional<Product> findById(Long id);

    Optional<Product> findBySlug(String slug);

    List<Product> findActiveProducts();

    List<Product> findBySellerId(Long sellerId);

    List<Product> findActiveProductsBySellerId(Long sellerId);

    Product update(
            Long productId,
            Long userId,
            boolean isAdmin,
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            List<Long> categoryIds
    );

    Product update(Long productId, Long userId, boolean isAdmin, ProductUpdateRequest request);

    void deactivate(
            Long productId,
            Long userId,
            boolean isAdmin
    );

    ProductPageResponse search(
            ProductSearchRequest request,
            Pageable pageable
    );

    Product updateStock(
            Long productId,
            Long userId,
            boolean isAdmin,
            Integer stockQuantity
    );
}
