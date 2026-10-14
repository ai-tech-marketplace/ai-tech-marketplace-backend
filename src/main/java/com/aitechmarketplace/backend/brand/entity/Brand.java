package com.aitechmarketplace.backend.brand.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "brands",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_brands_slug",
            columnNames = "slug"
        )
    }
)
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        nullable = false,
        length = 100
    )
    private String name;

    @Column(
        nullable = false,
        length = 150
    )
    private String slug;

    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private String status = "ACTIVE";

    @Column(
        name = "created_at",
        nullable = false
    )
    private OffsetDateTime createdAt;

    @Column(
        name = "updated_at",
        nullable = false
    )
    private OffsetDateTime updatedAt;

    public Brand() {}

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(this.status);
    }

    public void setActive(boolean active) {
        this.status = active ? "ACTIVE" : "INACTIVE";
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
