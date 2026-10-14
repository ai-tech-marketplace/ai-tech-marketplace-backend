package com.aitechmarketplace.backend.product.repository;

import com.aitechmarketplace.backend.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository
extends JpaRepository<Product, Long>,
JpaSpecificationExecutor<Product> {

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") Long id);

@EntityGraph(
    attributePaths = {
        "seller",
        "category",
        "brand",
        "images"
    }
)
Optional<Product> findById(Long id);

@EntityGraph(
    attributePaths = {
        "seller",
        "category",
        "brand",
        "images"
    }
)
Optional<Product> findBySlug(String slug);

@EntityGraph(
    attributePaths = {
        "seller",
        "category",
        "brand",
        "images"
    }
)
Optional<Product> findBySku(String sku);

@Query("SELECT p FROM Product p WHERE p.status = 'ACTIVE'")
@EntityGraph(
    attributePaths = {
        "seller",
        "category",
        "brand",
        "images"
    }
)
List<Product> findByActiveTrue();

@EntityGraph(
    attributePaths = {
        "seller",
        "category",
        "brand",
        "images"
    }
)
List<Product> findBySellerId(Long sellerId);

@Query("SELECT p FROM Product p WHERE p.seller.id = :sellerId AND p.status = 'ACTIVE'")
@EntityGraph(
    attributePaths = {
        "seller",
        "category",
        "brand",
        "images"
    }
)
List<Product> findBySellerIdAndActiveTrue(@Param("sellerId") Long sellerId);

@Override
@EntityGraph(
    attributePaths = {
        "seller",
        "category",
        "brand",
        "images"
    }
)
org.springframework.data.domain.Page<Product> findAll(
    org.springframework.data.jpa.domain.Specification<Product> spec,
    org.springframework.data.domain.Pageable pageable
);

boolean existsBySku(String sku);

boolean existsBySlug(String slug);

boolean existsByCategoryIdAndStatus(Long categoryId, String status);

boolean existsByBrandIdAndStatus(Long brandId, String status);

}