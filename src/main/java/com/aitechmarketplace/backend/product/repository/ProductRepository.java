package com.aitechmarketplace.backend.product.repository;

import com.aitechmarketplace.backend.product.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository
extends JpaRepository<Product, Long>,
JpaSpecificationExecutor<Product> {

@EntityGraph(
    attributePaths = {
        "seller",
        "category"
    }
)
Optional<Product> findById(Long id);

@Query("SELECT p FROM Product p WHERE p.status = 'ACTIVE'")
@EntityGraph(
    attributePaths = {
        "seller",
        "category"
    }
)
List<Product> findByActiveTrue();

@EntityGraph(
    attributePaths = {
        "seller",
        "category"
    }
)
List<Product> findBySellerId(Long sellerId);

@Query("SELECT p FROM Product p WHERE p.seller.id = :sellerId AND p.status = 'ACTIVE'")
@EntityGraph(
    attributePaths = {
        "seller",
        "category"
    }
)
List<Product> findBySellerIdAndActiveTrue(@Param("sellerId") Long sellerId);

boolean existsByCategoryIdAndStatus(Long categoryId, String status);

boolean existsByBrandIdAndStatus(Long brandId, String status);

}