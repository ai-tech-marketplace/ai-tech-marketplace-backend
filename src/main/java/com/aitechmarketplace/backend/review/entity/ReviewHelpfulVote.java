package com.aitechmarketplace.backend.review.entity;

import com.aitechmarketplace.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "review_helpful_votes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_review_helpful_votes_review_user", columnNames = {"review_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_review_helpful_votes_review_id", columnList = "review_id")
    }
)
public class ReviewHelpfulVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public ReviewHelpfulVote() {
    }

    public ReviewHelpfulVote(Review review, User user) {
        this.review = review;
        this.user = user;
        this.createdAt = OffsetDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Review getReview() {
        return review;
    }

    public void setReview(Review review) {
        this.review = review;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
