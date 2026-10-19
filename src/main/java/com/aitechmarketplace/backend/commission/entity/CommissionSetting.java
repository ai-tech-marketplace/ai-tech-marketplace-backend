package com.aitechmarketplace.backend.commission.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "commission_settings",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_commission_scope_target", columnNames = {"scope", "target_id"})
    }
)
public class CommissionSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommissionScope scope;

    @Column(name = "target_id")
    private Long targetId;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal rate;

    @Column(length = 255)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public CommissionSetting() {
    }

    public CommissionSetting(CommissionScope scope, Long targetId, BigDecimal rate, String description) {
        this.scope = scope;
        this.targetId = targetId;
        this.rate = rate;
        this.description = description;
        this.isActive = true;
    }

    public CommissionSetting(CommissionScope scope, Long targetId, BigDecimal rate, String description, boolean isActive) {
        this.scope = scope;
        this.targetId = targetId;
        this.rate = rate;
        this.description = description;
        this.isActive = isActive;
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

    public CommissionScope getScope() {
        return scope;
    }

    public void setScope(CommissionScope scope) {
        this.scope = scope;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean getIsActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setIsActive(boolean active) {
        this.isActive = active;
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
