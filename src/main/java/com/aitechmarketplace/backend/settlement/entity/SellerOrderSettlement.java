package com.aitechmarketplace.backend.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "seller_order_settlements",
    indexes = {
        @Index(name = "idx_settlements_seller_created", columnList = "seller_id, created_at"),
        @Index(name = "idx_settlements_seller_status", columnList = "seller_id, status"),
        @Index(name = "idx_settlements_order_id", columnList = "order_id")
    }
)
public class SellerOrderSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "item_subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal itemSubtotal;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "platform_discount_subsidized", nullable = false, precision = 15, scale = 2)
    private BigDecimal platformDiscountSubsidized = BigDecimal.ZERO;

    @Column(name = "seller_net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal sellerNetAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public SellerOrderSettlement() {
    }

    public SellerOrderSettlement(Long orderId, Long orderItemId, Long sellerId, BigDecimal itemSubtotal,
                                BigDecimal commissionRate, BigDecimal commissionAmount,
                                BigDecimal platformDiscountSubsidized, BigDecimal sellerNetAmount) {
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.sellerId = sellerId;
        this.itemSubtotal = itemSubtotal;
        this.commissionRate = commissionRate;
        this.commissionAmount = commissionAmount;
        this.platformDiscountSubsidized = platformDiscountSubsidized != null ? platformDiscountSubsidized : BigDecimal.ZERO;
        this.sellerNetAmount = sellerNetAmount;
        this.status = SettlementStatus.PENDING;
    }

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    public BigDecimal getItemSubtotal() {
        return itemSubtotal;
    }

    public void setItemSubtotal(BigDecimal itemSubtotal) {
        this.itemSubtotal = itemSubtotal;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public void setCommissionAmount(BigDecimal commissionAmount) {
        this.commissionAmount = commissionAmount;
    }

    public BigDecimal getPlatformDiscountSubsidized() {
        return platformDiscountSubsidized;
    }

    public void setPlatformDiscountSubsidized(BigDecimal platformDiscountSubsidized) {
        this.platformDiscountSubsidized = platformDiscountSubsidized;
    }

    public BigDecimal getSellerNetAmount() {
        return sellerNetAmount;
    }

    public void setSellerNetAmount(BigDecimal sellerNetAmount) {
        this.sellerNetAmount = sellerNetAmount;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public void setStatus(SettlementStatus status) {
        this.status = status;
    }

    public OffsetDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(OffsetDateTime settledAt) {
        this.settledAt = settledAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
