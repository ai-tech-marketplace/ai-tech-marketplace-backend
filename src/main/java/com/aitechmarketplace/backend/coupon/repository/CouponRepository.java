package com.aitechmarketplace.backend.coupon.repository;

import com.aitechmarketplace.backend.coupon.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long>, JpaSpecificationExecutor<Coupon> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("SELECT c FROM Coupon c WHERE c.isPublic = true AND c.status = 'ACTIVE' AND c.startDate <= :now AND c.endDate >= :now AND c.usedCount < c.usageLimit")
    Page<Coupon> findActivePublicCoupons(@Param("now") OffsetDateTime now, Pageable pageable);
}
