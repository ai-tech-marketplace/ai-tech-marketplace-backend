package com.aitechmarketplace.backend.cart.service;

import com.aitechmarketplace.backend.cart.dto.CartItemActionResponse;
import com.aitechmarketplace.backend.cart.dto.CartResponse;
import com.aitechmarketplace.backend.cart.entity.Cart;
import com.aitechmarketplace.backend.cart.entity.CartItem;
import com.aitechmarketplace.backend.cart.repository.CartItemRepository;
import com.aitechmarketplace.backend.cart.repository.CartRepository;
import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.image.entity.ProductImage;
import com.aitechmarketplace.backend.product.image.repository.ProductImageRepository;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    private CartService cartService;

    private User testUser;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(
                cartRepository,
                cartItemRepository,
                productRepository,
                userRepository,
                productImageRepository
        );

        testUser = new User();
        ReflectionTestUtils.setField(testUser, "id", 1L);
        testUser.setEmail("user@example.com");

        testProduct = new Product();
        ReflectionTestUtils.setField(testProduct, "id", 10L);
        testProduct.setName("Gaming Laptop");
        testProduct.setPrice(new BigDecimal("20000000.00"));
        testProduct.setStockQuantity(10);
        testProduct.setActive(true);
    }

    @Test
    void getCart_shouldReturnEmptyCart_whenNoCartExists() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CartResponse response = cartService.getCart(1L);

        assertThat(response).isNotNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isEqualTo(0);
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getCart_shouldCalculateSubtotalAndTotalAmount() {
        Cart cart = new Cart(testUser);
        ReflectionTestUtils.setField(cart, "id", 100L);

        CartItem item1 = new CartItem(cart, testProduct, 2);
        ReflectionTestUtils.setField(item1, "id", 1001L);

        Product secondProduct = new Product();
        ReflectionTestUtils.setField(secondProduct, "id", 20L);
        secondProduct.setName("Mouse");
        secondProduct.setPrice(new BigDecimal("500000.00"));
        secondProduct.setStockQuantity(5);
        secondProduct.setActive(true);

        CartItem item2 = new CartItem(cart, secondProduct, 1);
        ReflectionTestUtils.setField(item2, "id", 1002L);

        cart.setItems(new ArrayList<>(List.of(item1, item2)));

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of(item1, item2));

        ProductImage image = new ProductImage();
        ReflectionTestUtils.setField(image, "id", 55L);
        when(productImageRepository.findByProductIdOrderByDisplayOrderAscIdAsc(10L))
                .thenReturn(List.of(image));
        when(productImageRepository.findByProductIdOrderByDisplayOrderAscIdAsc(20L))
                .thenReturn(List.of());

        CartResponse response = cartService.getCart(1L);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.items()).hasSize(2);
        assertThat(response.totalItems()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("40500000.00"));
        assertThat(response.items().get(0).product().thumbnailUrl())
                .isEqualTo("/api/v1/products/10/images/55/content");
        assertThat(response.items().get(0).isAvailable()).isTrue();
        assertThat(response.items().get(1).product().thumbnailUrl()).isNull();
        assertThat(response.items().get(1).isAvailable()).isTrue();
    }

    @Test
    void addItem_shouldCreateCartAndAddItem_whenProductValid() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        Cart newCart = new Cart(testUser);
        ReflectionTestUtils.setField(newCart, "id", 100L);
        when(cartRepository.save(any(Cart.class))).thenReturn(newCart);

        when(cartItemRepository.findByCartIdAndProductId(100L, 10L)).thenReturn(Optional.empty());

        CartItem savedItem = new CartItem(newCart, testProduct, 2);
        ReflectionTestUtils.setField(savedItem, "id", 1001L);
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedItem);

        CartItemActionResponse response = cartService.addItem(1L, 10L, 2);

        assertThat(response.id()).isEqualTo(1001L);
        assertThat(response.productId()).isEqualTo(10L);
        assertThat(response.quantity()).isEqualTo(2);
    }

    @Test
    void addItem_shouldIncrementQuantity_whenItemAlreadyExists() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));

        Cart existingCart = new Cart(testUser);
        ReflectionTestUtils.setField(existingCart, "id", 100L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(existingCart));

        CartItem existingItem = new CartItem(existingCart, testProduct, 2);
        ReflectionTestUtils.setField(existingItem, "id", 1001L);
        when(cartItemRepository.findByCartIdAndProductId(100L, 10L)).thenReturn(Optional.of(existingItem));
        when(cartItemRepository.save(existingItem)).thenReturn(existingItem);

        CartItemActionResponse response = cartService.addItem(1L, 10L, 3);

        assertThat(response.id()).isEqualTo(1001L);
        assertThat(response.productId()).isEqualTo(10L);
        assertThat(response.quantity()).isEqualTo(5);
        assertThat(existingItem.getQuantity()).isEqualTo(5);
    }

    @Test
    void addItem_shouldThrowNotFoundException_whenProductDoesNotExist() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(1L, 99L, 1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Product not found");
    }

    @Test
    void addItem_shouldThrowBadRequestException_whenProductInactive() {
        testProduct.setActive(false);
        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));

        assertThatThrownBy(() -> cartService.addItem(1L, 10L, 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Product is not available");
    }

    @Test
    void addItem_shouldThrowBadRequestException_whenQuantityExceedsStock() {
        testProduct.setStockQuantity(2);
        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));

        assertThatThrownBy(() -> cartService.addItem(1L, 10L, 5))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Quantity exceeds available stock");
    }

    @Test
    void updateItem_shouldUpdateQuantity_whenValid() {
        Cart cart = new Cart(testUser);
        ReflectionTestUtils.setField(cart, "id", 100L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        CartItem item = new CartItem(cart, testProduct, 1);
        ReflectionTestUtils.setField(item, "id", 1001L);
        when(cartItemRepository.findByIdAndCartId(1001L, 100L)).thenReturn(Optional.of(item));
        when(cartItemRepository.save(item)).thenReturn(item);

        CartItemActionResponse response = cartService.updateItem(1L, 1001L, 4);

        assertThat(response.id()).isEqualTo(1001L);
        assertThat(response.productId()).isEqualTo(10L);
        assertThat(response.quantity()).isEqualTo(4);
        assertThat(item.getQuantity()).isEqualTo(4);
    }

    @Test
    void updateItem_shouldThrowNotFoundException_whenItemNotFoundOrNotOwned() {
        Cart cart = new Cart(testUser);
        ReflectionTestUtils.setField(cart, "id", 100L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(999L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem(1L, 999L, 2))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Cart item not found");
    }

    @Test
    void updateItem_shouldThrowBadRequestException_whenQuantityExceedsStock() {
        Cart cart = new Cart(testUser);
        ReflectionTestUtils.setField(cart, "id", 100L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        testProduct.setStockQuantity(3);
        CartItem item = new CartItem(cart, testProduct, 1);
        ReflectionTestUtils.setField(item, "id", 1001L);
        when(cartItemRepository.findByIdAndCartId(1001L, 100L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.updateItem(1L, 1001L, 5))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Quantity exceeds available stock");
    }

    @Test
    void removeItem_shouldRemoveItem_whenOwned() {
        Cart cart = new Cart(testUser);
        ReflectionTestUtils.setField(cart, "id", 100L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        CartItem item = new CartItem(cart, testProduct, 1);
        ReflectionTestUtils.setField(item, "id", 1001L);
        when(cartItemRepository.findByIdAndCartId(1001L, 100L)).thenReturn(Optional.of(item));

        cartService.removeItem(1L, 1001L);

        verify(cartItemRepository).delete(item);
    }

    @Test
    void removeItem_shouldThrowNotFoundException_whenNotOwned() {
        Cart cart = new Cart(testUser);
        ReflectionTestUtils.setField(cart, "id", 100L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(999L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(1L, 999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Cart item not found");
    }

    @Test
    void clearCart_shouldRemoveAllItems() {
        Cart cart = new Cart(testUser);
        ReflectionTestUtils.setField(cart, "id", 100L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        cartService.clearCart(1L);

        verify(cartItemRepository).deleteByCartId(100L);
    }

    @Test
    void merge_shouldClampQuantityWhenExceedsStock() {
        Cart cart = new Cart(testUser);
        ReflectionTestUtils.setField(cart, "id", 100L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        // testProduct has stock = 10
        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));
        // Existing item has quantity = 8
        CartItem existingItem = new CartItem(cart, testProduct, 8);
        when(cartItemRepository.findByCartIdAndProductId(100L, 10L)).thenReturn(Optional.of(existingItem));
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of(existingItem));

        // Requesting 5 more -> 8 + 5 = 13 > 10 -> clamped to 10
        com.aitechmarketplace.backend.cart.dto.CartMergeRequest request =
                new com.aitechmarketplace.backend.cart.dto.CartMergeRequest(
                        List.of(new com.aitechmarketplace.backend.cart.dto.CartItemAddRequest(10L, 5))
                );

        CartResponse response = cartService.merge(1L, request);

        assertThat(existingItem.getQuantity()).isEqualTo(10);
        verify(cartItemRepository).save(existingItem);
    }

    @Test
    void merge_shouldSkipInactiveOrZeroStockProducts() {
        Cart cart = new Cart(testUser);
        ReflectionTestUtils.setField(cart, "id", 100L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        Product inactiveProduct = new Product();
        ReflectionTestUtils.setField(inactiveProduct, "id", 20L);
        inactiveProduct.setActive(false);
        inactiveProduct.setStockQuantity(10);

        Product zeroStockProduct = new Product();
        ReflectionTestUtils.setField(zeroStockProduct, "id", 30L);
        zeroStockProduct.setActive(true);
        zeroStockProduct.setStockQuantity(0);

        when(productRepository.findById(20L)).thenReturn(Optional.of(inactiveProduct));
        when(productRepository.findById(30L)).thenReturn(Optional.of(zeroStockProduct));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of());

        com.aitechmarketplace.backend.cart.dto.CartMergeRequest request =
                new com.aitechmarketplace.backend.cart.dto.CartMergeRequest(
                        List.of(
                                new com.aitechmarketplace.backend.cart.dto.CartItemAddRequest(20L, 2),
                                new com.aitechmarketplace.backend.cart.dto.CartItemAddRequest(30L, 1),
                                new com.aitechmarketplace.backend.cart.dto.CartItemAddRequest(999L, 5)
                        )
                );

        CartResponse response = cartService.merge(1L, request);

        verify(cartItemRepository, never()).save(any());
        assertThat(response.items()).isEmpty();
    }
}
