package com.aitechmarketplace.backend.product.service;

import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void create_shouldCreateProductForSeller() {
        User seller = new User();
        seller.setEmail("seller@example.com");
        seller.setActive(true);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(seller));

        Product savedProduct = new Product();

        when(productRepository.save(any(Product.class)))
                .thenReturn(savedProduct);

        Product result = productService.create(
                1L,
                "MacBook Pro",
                "M4 MacBook",
                new BigDecimal("35000000.00"),
                5,
                List.of());

        assertEquals(savedProduct, result);

        verify(userRepository)
                .findById(1L);

        verify(productRepository)
                .save(any(Product.class));
    }

    @Test
    void create_withUnknownSeller_shouldThrowException() {
        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> productService.create(
                        999L,
                        "MacBook Pro",
                        "M4 MacBook",
                        new BigDecimal("35000000.00"),
                        5,
                        List.of()));

        assertEquals(
                "Seller not found",
                exception.getMessage());

        verify(productRepository, never())
                .save(any(Product.class));
    }

    @Test
    void create_withInactiveSeller_shouldThrowException() {
        User seller = new User();
        seller.setActive(false);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(seller));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> productService.create(
                        1L,
                        "MacBook Pro",
                        "M4 MacBook",
                        new BigDecimal("35000000.00"),
                        5,
                        List.of()));

        assertEquals(
                "Seller account is inactive",
                exception.getMessage());

        verify(categoryRepository, never())
                .findAllById(any());

        verify(productRepository, never())
                .save(any(Product.class));
    }

    @Test
    void update_whenUserDoesNotOwnProduct_shouldBeRejected() {
        User owner = new User();
        owner.setActive(true);

        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(owner, 1L);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        Product product = new Product();
        product.setSeller(owner);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> productService.update(
                        1L,
                        999L,
                        "Updated",
                        "Updated",
                        new BigDecimal("1000000.00"),
                        1,
                        List.of()));

        assertEquals(
                "You do not own this product",
                exception.getMessage());

        verify(categoryRepository, never())
                .findAllById(any());

        verify(productRepository, never())
                .save(any(Product.class));
    }

}
