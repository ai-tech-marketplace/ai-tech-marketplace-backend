package com.aitechmarketplace.backend.category.service;

import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void create_shouldCreateCategoryWithAutoSlug() {
        when(categoryRepository.existsBySlug("electronics"))
                .thenReturn(false);

        Category savedCategory = new Category();
        savedCategory.setName("Electronics");
        savedCategory.setSlug("electronics");

        when(categoryRepository.save(any(Category.class)))
                .thenReturn(savedCategory);

        Category result = categoryService.create(
                "Electronics", null, "Electronic products");

        assertEquals(savedCategory, result);
        verify(categoryRepository).existsBySlug("electronics");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void create_withExplicitSlug_shouldUseProvidedSlug() {
        when(categoryRepository.existsBySlug("custom-slug"))
                .thenReturn(false);

        Category savedCategory = new Category();
        savedCategory.setName("Electronics");
        savedCategory.setSlug("custom-slug");

        when(categoryRepository.save(any(Category.class)))
                .thenReturn(savedCategory);

        Category result = categoryService.create(
                "Electronics", "Custom Slug", "Electronic products");

        assertEquals("custom-slug", result.getSlug());
        verify(categoryRepository).existsBySlug("custom-slug");
    }

    @Test
    void create_withDuplicateSlug_shouldThrowException() {
        when(categoryRepository.existsBySlug("electronics"))
                .thenReturn(true);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> categoryService.create(
                        "Electronics", null, "Electronic products"));

        assertEquals("Category slug already exists", exception.getMessage());
        assertEquals("SLUG_ALREADY_EXISTS", exception.getErrorCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void findActiveCategories_shouldReturnActiveCategories() {
        Category category = new Category();
        category.setName("Electronics");
        category.setSlug("electronics");

        when(categoryRepository.findByActiveTrue())
                .thenReturn(List.of(category));

        List<Category> result = categoryService.findActiveCategories();

        assertEquals(1, result.size());
        assertEquals("Electronics", result.get(0).getName());
        verify(categoryRepository).findByActiveTrue();
    }

    @Test
    void update_shouldUpdateCategory() {
        Category category = new Category();
        category.setName("Electronics");
        category.setSlug("electronics");
        category.setDescription("Old description");

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));
        when(categoryRepository.existsBySlug("computers"))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenReturn(category);

        Category result = categoryService.update(
                1L, "Computers", null, "Computer products", null);

        assertEquals("Computers", result.getName());
        assertEquals("computers", result.getSlug());
        assertEquals("Computer products", result.getDescription());
        verify(categoryRepository).findById(1L);
        verify(categoryRepository).save(category);
    }

    @Test
    void update_withExplicitSlugAndStatus_shouldApplyBoth() {
        Category category = new Category();
        category.setName("Electronics");
        category.setSlug("electronics");
        category.setStatus("ACTIVE");

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));
        when(categoryRepository.existsBySlug("new-slug"))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenReturn(category);

        Category result = categoryService.update(
                1L, "Electronics", "New Slug", null, "INACTIVE");

        assertEquals("new-slug", result.getSlug());
        assertEquals("INACTIVE", result.getStatus());
        verify(categoryRepository).save(category);
    }

    @Test
    void update_withUnknownCategory_shouldThrowException() {
        when(categoryRepository.findById(999L))
                .thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> categoryService.update(
                        999L, "Computers", null, "Computer products", null));

        assertEquals("Category not found", exception.getMessage());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deactivate_shouldDeactivateCategory() {
        Category category = new Category();
        category.setName("Electronics");
        category.setStatus("ACTIVE");

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryIdAndStatus(1L, "ACTIVE"))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenReturn(category);

        categoryService.deactivate(1L);

        assertFalse(category.isActive());
        assertEquals("INACTIVE", category.getStatus());
        verify(categoryRepository).save(category);
    }

    @Test
    void deactivate_withActiveProducts_shouldThrowConflictException() {
        Category category = new Category();
        category.setName("Electronics");
        category.setStatus("ACTIVE");

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryIdAndStatus(1L, "ACTIVE"))
                .thenReturn(true);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> categoryService.deactivate(1L)
        );

        assertEquals("Cannot deactivate category currently in use by active products", exception.getMessage());
        assertEquals("RESOURCE_IN_USE", exception.getErrorCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deactivate_withUnknownCategory_shouldThrowException() {
        when(categoryRepository.findById(999L))
                .thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> categoryService.deactivate(999L));

        assertEquals("Category not found", exception.getMessage());
        verify(categoryRepository, never()).save(any(Category.class));
    }
}
