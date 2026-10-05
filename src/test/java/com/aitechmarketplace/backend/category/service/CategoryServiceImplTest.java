package com.aitechmarketplace.backend.category.service;

import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void create_shouldCreateCategory() {
        when(categoryRepository.existsByName("Electronics"))
                .thenReturn(false);

        Category savedCategory = new Category();
        savedCategory.setName("Electronics");

        when(categoryRepository.save(any(Category.class)))
                .thenReturn(savedCategory);

        Category result = categoryService.create(
                "Electronics",
                "Electronic products");

        assertEquals(
                savedCategory,
                result);

        verify(categoryRepository)
                .existsByName("Electronics");

        verify(categoryRepository)
                .save(any(Category.class));
    }

    @Test
    void create_withDuplicateName_shouldThrowException() {
        when(categoryRepository.existsByName("Electronics"))
                .thenReturn(true);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> categoryService.create(
                        "Electronics",
                        "Electronic products"));

        assertEquals(
                "Category name already exists",
                exception.getMessage());

        verify(categoryRepository, never())
                .save(any(Category.class));
    }

    @Test
    void findActiveCategories_shouldReturnActiveCategories() {
        Category category = new Category();
        category.setName("Electronics");

        when(categoryRepository.findByActiveTrue())
                .thenReturn(List.of(category));

        List<Category> result = categoryService.findActiveCategories();

        assertEquals(1, result.size());
        assertEquals(
                "Electronics",
                result.get(0).getName());

        verify(categoryRepository)
                .findByActiveTrue();
    }

    @Test
    void update_shouldUpdateCategory() {
        Category category = new Category();
        category.setName("Electronics");
        category.setDescription("Old description");

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(categoryRepository.save(any(Category.class)))
                .thenReturn(category);

        Category result = categoryService.update(
                1L,
                "Computers",
                "Computer products");

        assertEquals(
                "Computers",
                result.getName());

        assertEquals(
                "Computer products",
                result.getDescription());

        verify(categoryRepository)
                .findById(1L);

        verify(categoryRepository)
                .save(category);
    }

    @Test
    void update_withUnknownCategory_shouldThrowException() {
        when(categoryRepository.findById(999L))
                .thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> categoryService.update(
                        999L,
                        "Computers",
                        "Computer products"));

        assertEquals(
                "Category not found",
                exception.getMessage());

        verify(categoryRepository, never())
                .save(any(Category.class));
    }

    @Test
    void update_withDuplicateName_shouldThrowException() {
        Category category = new Category();
        category.setName("Electronics");

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(categoryRepository.existsByName("Computers"))
                .thenReturn(true);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> categoryService.update(
                        1L,
                        "Computers",
                        "Computer products"));

        assertEquals(
                "Category name already exists",
                exception.getMessage());

        verify(categoryRepository, never())
                .save(any(Category.class));
    }

    @Test
    void deactivate_shouldDeactivateCategory() {
        Category category = new Category();
        category.setName("Electronics");
        category.setActive(true);

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(categoryRepository.save(any(Category.class)))
                .thenReturn(category);

        categoryService.deactivate(1L);

        assertFalse(category.isActive());

        verify(categoryRepository)
                .save(category);
    }

    @Test
    void deactivate_withUnknownCategory_shouldThrowException() {
        when(categoryRepository.findById(999L))
                .thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> categoryService.deactivate(999L));

        assertEquals(
                "Category not found",
                exception.getMessage());

        verify(categoryRepository, never())
                .save(any(Category.class));
    }
}
