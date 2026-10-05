package com.aitechmarketplace.backend.category.repository;

import com.aitechmarketplace.backend.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository
    extends JpaRepository<Category, Long> {

    List<Category> findByActiveTrue();

    boolean existsByName(String name);
}
