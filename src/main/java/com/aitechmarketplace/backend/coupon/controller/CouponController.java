package com.aitechmarketplace.backend.coupon.controller;

import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.coupon.dto.CouponPageResponse;
import com.aitechmarketplace.backend.coupon.dto.CouponValidateRequest;
import com.aitechmarketplace.backend.coupon.dto.CouponValidateResponse;
import com.aitechmarketplace.backend.coupon.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CouponPageResponse>> getPublicCoupons(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        CouponPageResponse response = CouponPageResponse.from(couponService.getPublicCoupons(userId, pageable));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<CouponValidateResponse>> validateCoupon(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody CouponValidateRequest request
    ) {
        CouponValidateResponse response = couponService.validateCoupon(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
