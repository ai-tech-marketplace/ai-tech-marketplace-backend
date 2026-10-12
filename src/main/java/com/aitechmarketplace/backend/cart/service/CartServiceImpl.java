package com.aitechmarketplace.backend.cart.service;

import com.aitechmarketplace.backend.cart.dto.CartItemActionResponse;
import com.aitechmarketplace.backend.cart.dto.CartItemResponse;
import com.aitechmarketplace.backend.cart.dto.CartProductResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductImageRepository productImageRepository;

    public CartServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            ProductImageRepository productImageRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.productImageRepository = productImageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(this::mapToCartResponse)
                .orElseGet(() -> new CartResponse(null, List.of(), 0, BigDecimal.ZERO));
    }

    @Override
    public CartItemActionResponse addItem(Long userId, Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        if (!product.isActive()) {
            throw new BadRequestException("Product is not available");
        }

        if (quantity > product.getStockQuantity()) {
            throw new BadRequestException("Quantity exceeds available stock");
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new NotFoundException("User not found"));
                    return cartRepository.save(new Cart(user));
                });

        return cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .map(existingItem -> {
                    int newQuantity = existingItem.getQuantity() + quantity;
                    if (newQuantity > product.getStockQuantity()) {
                        throw new BadRequestException("Quantity exceeds available stock");
                    }
                    existingItem.setQuantity(newQuantity);
                    CartItem updated = cartItemRepository.save(existingItem);
                    return new CartItemActionResponse(updated.getId(), productId, updated.getQuantity());
                })
                .orElseGet(() -> {
                    CartItem newItem = cartItemRepository.save(new CartItem(cart, product, quantity));
                    return new CartItemActionResponse(newItem.getId(), productId, newItem.getQuantity());
                });
    }

    @Override
    public CartItemActionResponse updateItem(Long userId, Long cartItemId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Cart item not found"));

        CartItem item = cartItemRepository.findByIdAndCartId(cartItemId, cart.getId())
                .orElseThrow(() -> new NotFoundException("Cart item not found"));

        if (quantity > item.getProduct().getStockQuantity()) {
            throw new BadRequestException("Quantity exceeds available stock");
        }

        item.setQuantity(quantity);
        CartItem saved = cartItemRepository.save(item);

        return new CartItemActionResponse(saved.getId(), saved.getProduct().getId(), saved.getQuantity());
    }

    @Override
    public void removeItem(Long userId, Long cartItemId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Cart item not found"));

        CartItem item = cartItemRepository.findByIdAndCartId(cartItemId, cart.getId())
                .orElseThrow(() -> new NotFoundException("Cart item not found"));

        cartItemRepository.delete(item);
    }

    @Override
    public void clearCart(Long userId) {
        cartRepository.findByUserId(userId)
                .ifPresent(cart -> cartItemRepository.deleteByCartId(cart.getId()));
    }

    private CartResponse mapToCartResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());

        List<CartItemResponse> itemResponses = items.stream()
                .map(item -> {
                    Product product = item.getProduct();
                    String thumbnailUrl = resolveThumbnailUrl(product.getId());

                    CartProductResponse productResponse = new CartProductResponse(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            thumbnailUrl
                    );

                    BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

                    return new CartItemResponse(
                            item.getId(),
                            productResponse,
                            item.getQuantity(),
                            subtotal
                    );
                })
                .toList();

        int totalItems = itemResponses.stream()
                .mapToInt(CartItemResponse::quantity)
                .sum();

        BigDecimal totalAmount = itemResponses.stream()
                .map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(cart.getId(), itemResponses, totalItems, totalAmount);
    }

    private String resolveThumbnailUrl(Long productId) {
        List<ProductImage> images = productImageRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);
        if (images.isEmpty()) {
            return null;
        }
        return "/api/products/" + productId + "/images/" + images.get(0).getId() + "/content";
    }
}
