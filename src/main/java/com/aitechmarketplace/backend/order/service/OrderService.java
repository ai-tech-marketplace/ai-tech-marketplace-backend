package com.aitechmarketplace.backend.order.service;

import com.aitechmarketplace.backend.order.dto.OrderCreateRequest;
import com.aitechmarketplace.backend.order.dto.OrderPageResponse;
import com.aitechmarketplace.backend.order.dto.OrderResponse;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse createOrder(Long userId, OrderCreateRequest request);

    OrderPageResponse getUserOrders(Long userId, OrderStatus status, Pageable pageable);

    OrderResponse getOrderById(Long orderId, Long userId, boolean isAdmin);

    OrderResponse cancelOrder(Long orderId, Long userId, boolean isAdmin);
}
