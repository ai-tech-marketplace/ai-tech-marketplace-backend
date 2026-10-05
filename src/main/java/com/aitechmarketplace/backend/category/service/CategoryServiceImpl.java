package com.aitechmarketplace.backend.category.service;

import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
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
        String description
    ) {
        if (categoryRepository.existsByName(name)) {
            throw new ConflictException(
                "Category name already exists"
            );
        }

        Category category = new Category();

        category.setName(name);
        category.setDescription(description);

        return categoryRepository.save(category);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findById(Long id) {
        return categoryRepository.findById(id);
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
        String description
    ) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() ->
                new NotFoundException("Category not found")
            );

        if (
            !category.getName().equals(name) &&
            categoryRepository.existsByName(name)
        ) {
            throw new ConflictException(
                "Category name already exists"
            );
        }

        category.setName(name);
        category.setDescription(description);

        return categoryRepository.save(category);
    }

    @Override
    public void deactivate(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() ->
                new NotFoundException("Category not found")
            );

        category.setActive(false);

        categoryRepository.save(category);
    }
}
