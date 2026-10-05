package com.aitechmarketplace.backend.product.repository;

import com.aitechmarketplace.backend.product.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository
    extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "seller")
    Optional<Product> findById(Long id);

    @EntityGraph(attributePaths = "seller")
    List<Product> findByActiveTrue();

    @EntityGraph(attributePaths = "seller")
    List<Product> findBySellerId(Long sellerId);

    @EntityGraph(attributePaths = "seller")
    List<Product> findBySellerIdAndActiveTrue(Long sellerId);
}
