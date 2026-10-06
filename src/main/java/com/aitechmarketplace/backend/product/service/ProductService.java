package com.aitechmarketplace.backend.product.service;

import com.aitechmarketplace.backend.product.dto.ProductPageResponse;
import com.aitechmarketplace.backend.product.dto.ProductSearchRequest;
import com.aitechmarketplace.backend.product.entity.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

public interface ProductService {

    Product create(
            Long sellerId,
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            List<Long> categoryIds
    );

    Optional<Product> findById(Long id);

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
