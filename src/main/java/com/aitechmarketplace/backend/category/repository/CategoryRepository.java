package com.aitechmarketplace.backend.category.repository;

import com.aitechmarketplace.backend.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("SELECT c FROM Category c WHERE c.status = 'ACTIVE'")
    List<Category> findByActiveTrue();

    List<Category> findByStatus(String status);

    Optional<Category> findBySlug(String slug);

    boolean existsByName(String name);

    boolean existsBySlug(String slug);
}
