package com.aitechmarketplace.backend.category.service;

import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.common.util.SlugUtils;
import com.aitechmarketplace.backend.product.entity.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(
        CategoryRepository categoryRepository
    ) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Category create(
        String name,
        String slug,
        String description
    ) {
        String finalSlug = (slug != null && !slug.isBlank())
                ? SlugUtils.toSlug(slug)
                : SlugUtils.toSlug(name);

        if (categoryRepository.existsBySlug(finalSlug)) {
            throw new ConflictException(
                "Category slug already exists",
                "SLUG_ALREADY_EXISTS"
            );
        }

        Category category = new Category();
        category.setName(name);
        category.setSlug(finalSlug);
        category.setDescription(description);
        category.setStatus("ACTIVE");

        return categoryRepository.save(category);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findById(Long id) {
        return categoryRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findBySlug(String slug) {
        return categoryRepository.findBySlug(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findActiveCategories() {
        return categoryRepository.findByActiveTrue();
    }

    @Override
    public Category update(
        Long categoryId,
        String name,
        String slug,
        String description,
        String status
    ) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() ->
                new NotFoundException("Category not found", "CATEGORY_NOT_FOUND")
            );

        String finalSlug = (slug != null && !slug.isBlank())
                ? SlugUtils.toSlug(slug)
                : (name != null ? SlugUtils.toSlug(name) : category.getSlug());

        if (!category.getSlug().equals(finalSlug) && categoryRepository.existsBySlug(finalSlug)) {
            throw new ConflictException(
                "Category slug already exists",
                "SLUG_ALREADY_EXISTS"
            );
        }

        if (name != null && !name.isBlank()) {
            category.setName(name);
        }
        category.setSlug(finalSlug);
        category.setDescription(description);

        if (status != null && !status.isBlank()) {
            category.setStatus(status);
        }

        return categoryRepository.save(category);
    }

    @Override
    public void deactivate(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() ->
                new NotFoundException("Category not found", "CATEGORY_NOT_FOUND")
            );

        boolean hasActiveProducts = category.getProducts() != null && category.getProducts().stream()
                .anyMatch(Product::isActive);

        if (hasActiveProducts) {
            throw new ConflictException(
                "Cannot deactivate category currently in use by active products",
                "RESOURCE_IN_USE"
            );
        }

        category.setStatus("INACTIVE");
        categoryRepository.save(category);
    }
}
