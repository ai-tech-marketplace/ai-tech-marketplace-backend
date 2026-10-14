package com.aitechmarketplace.backend.cart.service;

import com.aitechmarketplace.backend.cart.dto.CartItemActionResponse;
import com.aitechmarketplace.backend.cart.dto.CartMergeRequest;
import com.aitechmarketplace.backend.cart.dto.CartResponse;

public interface CartService {

    CartResponse getCart(Long userId);

    CartItemActionResponse addItem(Long userId, Long productId, Integer quantity);

    CartItemActionResponse updateItem(Long userId, Long cartItemId, Integer quantity);

    void removeItem(Long userId, Long cartItemId);

    void clearCart(Long userId);

    CartResponse merge(Long userId, CartMergeRequest request);
}
