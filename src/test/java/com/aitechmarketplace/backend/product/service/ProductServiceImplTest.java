package com.aitechmarketplace.backend.product.service;

import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.repository.BrandRepository;
import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.dto.ProductCreateRequest;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void create_shouldCreateProductForSeller() {
        User seller = new User();
        seller.setEmail("seller@example.com");
        seller.setActive(true);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(seller));

        Category category = new Category();
        category.setId(10L);
        category.setName("Electronics");
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        Product savedProduct = new Product();

        when(productRepository.save(any(Product.class)))
                .thenReturn(savedProduct);

        Product result = productService.create(
                1L,
                "MacBook Pro",
                "M4 MacBook",
                new BigDecimal("35000000.00"),
                5,
                List.of(10L));

        assertEquals(savedProduct, result);

        verify(userRepository)
                .findById(1L);

        verify(productRepository)
                .save(any(Product.class));
    }

    @Test
    void create_withExplicitSkuAndSlug_shouldUseThem() {
        User seller = new User();
        seller.setEmail("seller@example.com");
        seller.setActive(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));

        Category category = new Category();
        category.setId(10L);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        Brand brand = new Brand();
        brand.setId(20L);
        brand.setName("Apple");
        when(brandRepository.findById(20L)).thenReturn(Optional.of(brand));

        when(productRepository.existsBySku("MBP-M4-001")).thenReturn(false);
        when(productRepository.existsBySlug("macbook-pro-m4")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductCreateRequest request = new ProductCreateRequest(
                "MBP-M4-001",
                "MacBook Pro M4",
                "macbook-pro-m4",
                "Description",
                new BigDecimal("45000000.00"),
                10,
                10L,
                null,
                20L,
                null,
                Map.of("ram", "32GB")
        );

        Product result = productService.create(1L, request);

        assertNotNull(result);
        assertEquals("MBP-M4-001", result.getSku());
        assertEquals("macbook-pro-m4", result.getSlug());
        assertEquals("Apple", result.getBrand().getName());
        assertEquals("32GB", result.getSpecifications().get("ram"));
    }

    @Test
    void create_withDuplicateSku_shouldThrowConflictException() {
        User seller = new User();
        seller.setActive(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));

        Category category = new Category();
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        when(productRepository.existsBySku("DUP-SKU")).thenReturn(true);

        ProductCreateRequest request = new ProductCreateRequest(
                "DUP-SKU", "Product", "product", "Desc", BigDecimal.TEN, 5, 10L, null, null, null, null);

        ConflictException ex = assertThrows(ConflictException.class, () -> productService.create(1L, request));
        assertEquals("Product SKU already exists", ex.getMessage());
        assertEquals("SKU_ALREADY_EXISTS", ex.getErrorCode());
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
                        List.of(1L)));

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
                        List.of(1L)));

        assertEquals(
                "Seller account is inactive",
                exception.getMessage());

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
                        false,
                        "Updated",
                        "Updated",
                        new BigDecimal("1000000.00"),
                        1,
                        List.of()));

        assertEquals(
                "You do not own this product",
                exception.getMessage());

        verify(productRepository, never())
                .save(any(Product.class));
    }

    @Test
    void updateStock_whenUserOwnsProduct_shouldUpdateStock() {
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
        product.setStockQuantity(5);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(productRepository.save(product))
                .thenReturn(product);

        Product result = productService.updateStock(
                1L,
                1L,
                false,
                20
        );

        assertEquals(product, result);
        assertEquals(20, result.getStockQuantity());

        verify(productRepository)
                .findById(1L);

        verify(productRepository)
                .save(product);
    }

    @Test
    void updateStock_whenUserDoesNotOwnProduct_shouldBeRejected() {
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
        product.setStockQuantity(5);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> productService.updateStock(
                        1L,
                        999L,
                        false,
                        20
                ));

        assertEquals(
                "You are not allowed to update stock for this product",
                exception.getMessage());

        verify(productRepository, never())
                .save(any(Product.class));
    }
}
