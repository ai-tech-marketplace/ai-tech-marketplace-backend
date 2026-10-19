package com.aitechmarketplace.backend.order.entity;

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED,
    CANCELLED,
    PAID_AFTER_CANCELLED
}
