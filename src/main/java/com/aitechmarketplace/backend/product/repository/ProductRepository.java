package com.aitechmarketplace.backend.product.repository;

import com.aitechmarketplace.backend.product.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ProductRepository
extends JpaRepository<Product, Long>,
JpaSpecificationExecutor<Product> {

@EntityGraph(
    attributePaths = {
        "seller",
        "categories"
    }
)
Optional<Product> findById(Long id);

@EntityGraph(
    attributePaths = {
        "seller",
        "categories"
    }
)
List<Product> findByActiveTrue();

@EntityGraph(
    attributePaths = {
        "seller",
        "categories"
    }
)
List<Product> findBySellerId(Long sellerId);

@EntityGraph(
    attributePaths = {
        "seller",
        "categories"
    }
)
List<Product> findBySellerIdAndActiveTrue(Long sellerId);

}