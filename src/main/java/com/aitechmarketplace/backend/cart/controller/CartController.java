package com.aitechmarketplace.backend.cart.controller;

import com.aitechmarketplace.backend.cart.dto.*;
import com.aitechmarketplace.backend.cart.service.CartService;
import com.aitechmarketplace.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/cart", "/api/cart"})
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartResponse> getCart(
            @RequestAttribute("userId") Long userId
    ) {
        CartResponse cart = cartService.getCart(userId);
        return ApiResponse.success(cart);
    }

    @PostMapping("/items")
    public ApiResponse<CartItemActionResponse> addItem(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody CartItemAddRequest request
    ) {
        CartItemActionResponse response = cartService.addItem(
                userId,
                request.productId(),
                request.quantity()
        );
        return ApiResponse.success(response, "Product added to cart");
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<CartItemActionResponse> updateItem(
            @PathVariable Long itemId,
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody CartItemUpdateRequest request
    ) {
        CartItemActionResponse response = cartService.updateItem(
                userId,
                itemId,
                request.quantity()
        );
        return ApiResponse.success(response, "Cart item updated");
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(
            @PathVariable Long itemId,
            @RequestAttribute("userId") Long userId
    ) {
        cartService.removeItem(userId, itemId);
    }

    @DeleteMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(
            @RequestAttribute("userId") Long userId
    ) {
        cartService.clearCart(userId);
    }

    @PostMapping("/merge")
    public ApiResponse<CartResponse> merge(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody CartMergeRequest request
    ) {
        CartResponse cart = cartService.merge(userId, request);
        return ApiResponse.success(cart, "Cart merged successfully");
    }
}
