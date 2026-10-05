package com.aitechmarketplace.backend.category.service;

import com.aitechmarketplace.backend.category.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryService {

    Category create(
        String name,
        String description
    );

    Optional<Category> findById(Long id);

    List<Category> findActiveCategories();

    Category update(
        Long categoryId,
        String name,
        String description
    );

    void deactivate(Long categoryId);
}
