package com.aitechmarketplace.backend.coupon.controller;

import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.coupon.dto.*;
import com.aitechmarketplace.backend.coupon.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/coupons")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CouponPageResponse>> getAdminCoupons(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        CouponPageResponse response = CouponPageResponse.from(couponService.getAdminCoupons(status, search, pageable));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @Valid @RequestBody CouponCreateRequest request
    ) {
        CouponResponse created = couponService.createCoupon(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Coupon created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponResponse>> updateCoupon(
            @PathVariable Long id,
            @Valid @RequestBody CouponUpdateRequest request
    ) {
        CouponResponse updated = couponService.updateCoupon(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Coupon updated successfully"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CouponResponse>> updateCouponStatus(
            @PathVariable Long id,
            @Valid @RequestBody CouponStatusUpdateRequest request
    ) {
        CouponResponse updated = couponService.updateCouponStatus(id, request.status());
        return ResponseEntity.ok(ApiResponse.success(updated, "Coupon status updated successfully"));
    }
}
