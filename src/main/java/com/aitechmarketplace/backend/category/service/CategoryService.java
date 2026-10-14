package com.aitechmarketplace.backend.category.service;

import com.aitechmarketplace.backend.category.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryService {

    Category create(String name, String slug, String description);

    default Category create(String name, String description) {
        return create(name, null, description);
    }

    Optional<Category> findById(Long id);

    Optional<Category> findBySlug(String slug);

    List<Category> findActiveCategories();

    Category update(Long categoryId, String name, String slug, String description, String status);

    default Category update(Long categoryId, String name, String description) {
        return update(categoryId, name, null, description, null);
    }

    void deactivate(Long categoryId);
}
