package com.aitechmarketplace.backend.product.service;

import com.aitechmarketplace.backend.product.entity.Product;

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
            List<Long> categoryIds);

    Optional<Product> findById(Long id);

    List<Product> findActiveProducts();

    List<Product> findBySellerId(Long sellerId);

    List<Product> findActiveProductsBySellerId(Long sellerId);

    Product update(
            Long productId,
            Long sellerId,
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            List<Long> categoryIds);

    void deactivate(
            Long productId,
            Long sellerId);

}
