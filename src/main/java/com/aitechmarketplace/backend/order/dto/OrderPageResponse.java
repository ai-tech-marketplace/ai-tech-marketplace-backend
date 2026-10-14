package com.aitechmarketplace.backend.order.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record OrderPageResponse(
        List<OrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static OrderPageResponse from(Page<OrderResponse> result) {
        return new OrderPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
