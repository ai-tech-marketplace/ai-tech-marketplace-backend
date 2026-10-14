package com.aitechmarketplace.backend.coupon.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record CouponPageResponse(
        List<CouponResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static CouponPageResponse from(Page<CouponResponse> result) {
        return new CouponPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
