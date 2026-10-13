package com.aitechmarketplace.backend.category.controller;

import com.aitechmarketplace.backend.category.dto.CategoryCreateRequest;
import com.aitechmarketplace.backend.category.dto.CategoryResponse;
import com.aitechmarketplace.backend.category.dto.CategoryUpdateRequest;
import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.service.CategoryService;
import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/api/v1/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> findActiveCategories() {
        List<CategoryResponse> data = categoryService.findActiveCategories()
                .stream()
                .map(CategoryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/api/v1/categories/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> findById(@PathVariable Long id) {
        Category category = categoryService.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found", "CATEGORY_NOT_FOUND"));
        return ResponseEntity.ok(ApiResponse.success(CategoryResponse.from(category)));
    }

    @PostMapping("/api/v1/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CategoryCreateRequest request
    ) {
        Category category = categoryService.create(
                request.name(),
                request.slug(),
                request.description()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(CategoryResponse.from(category), "Category created successfully"));
    }

    @PutMapping("/api/v1/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateRequest request
    ) {
        Category category = categoryService.update(
                id,
                request.name(),
                request.slug(),
                request.description(),
                request.status()
        );
        return ResponseEntity.ok(ApiResponse.success(CategoryResponse.from(category), "Category updated successfully"));
    }

    @DeleteMapping("/api/v1/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        categoryService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
