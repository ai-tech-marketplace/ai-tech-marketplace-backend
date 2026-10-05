package com.aitechmarketplace.backend.category.controller;

import com.aitechmarketplace.backend.category.dto.CategoryCreateRequest;
import com.aitechmarketplace.backend.category.dto.CategoryResponse;
import com.aitechmarketplace.backend.category.dto.CategoryUpdateRequest;
import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(
        CategoryService categoryService
    ) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(
        @Valid @RequestBody CategoryCreateRequest request
    ) {
        Category category = categoryService.create(
            request.name(),
            request.description()
        );

        return CategoryResponse.from(category);
    }

    @GetMapping
    public List<CategoryResponse> findActiveCategories() {
        return categoryService.findActiveCategories()
            .stream()
            .map(CategoryResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public CategoryResponse findById(
        @PathVariable Long id
    ) {
        Category category = categoryService.findById(id)
            .orElseThrow(() ->
                new com.aitechmarketplace.backend.common.exception.NotFoundException(
                    "Category not found"
                )
            );

        return CategoryResponse.from(category);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(
        @PathVariable Long id,
        @Valid @RequestBody CategoryUpdateRequest request
    ) {
        Category category = categoryService.update(
            id,
            request.name(),
            request.description()
        );

        return CategoryResponse.from(category);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
        @PathVariable Long id
    ) {
        categoryService.deactivate(id);
    }
}
