package com.aitechmarketplace.backend.coupon.repository;

import com.aitechmarketplace.backend.coupon.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    long countByCouponIdAndUserIdAndStatus(Long couponId, Long userId, String status);

    Optional<CouponUsage> findByOrderIdAndStatus(Long orderId, String status);

    Optional<CouponUsage> findByOrderId(Long orderId);
}
