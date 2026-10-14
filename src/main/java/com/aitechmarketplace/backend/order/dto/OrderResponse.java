package com.aitechmarketplace.backend.order.dto;

import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.order.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String paymentUrl,
        OffsetDateTime expiresAt,
        List<OrderItemResponse> items,
        ShippingAddressDto shippingAddress,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static OrderResponse from(Order order) {
        return from(order, null);
    }

    public static OrderResponse from(Order order, String paymentUrl) {
        List<OrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream().map(OrderItemResponse::from).toList()
                : Collections.emptyList();

        OffsetDateTime expiresAt = null;
        if (order.getStatus() == OrderStatus.PENDING && order.getPaymentStatus() == PaymentStatus.PENDING) {
            expiresAt = order.getCreatedAt() != null ? order.getCreatedAt().plusMinutes(15) : OffsetDateTime.now().plusMinutes(15);
        }

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getSubtotal(),
                order.getShippingFee(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                paymentUrl,
                expiresAt,
                itemResponses,
                ShippingAddressDto.from(order),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
