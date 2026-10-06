package com.aitechmarketplace.backend.product.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record ProductPageResponse(
List<ProductResponse> content,
int page,
int size,
long totalElements,
int totalPages
) {

public static ProductPageResponse from(
    Page<ProductResponse> result
) {
    return new ProductPageResponse(
        result.getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages()
    );
}

}