package com.aitechmarketplace.backend.product.controller;

import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.dto.*;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping({"/api/v1/products", "/api/products"})
    public ResponseEntity<ApiResponse<ProductPageResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ProductSearchRequest request = new ProductSearchRequest(
                keyword,
                categoryId,
                brandId,
                minPrice,
                maxPrice
        );

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        ProductPageResponse response = productService.search(request, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping({"/api/v1/products/{id}", "/api/products/{id}"})
    public ResponseEntity<ApiResponse<ProductDetailResponse>> findById(@PathVariable Long id) {
        Product product = productService.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found", "PRODUCT_NOT_FOUND"));

        return ResponseEntity.ok(ApiResponse.success(ProductDetailResponse.from(product)));
    }

    @PostMapping({"/api/v1/products", "/api/products"})
    public ResponseEntity<ApiResponse<ProductDetailResponse>> create(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        Product product = productService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ProductDetailResponse.from(product), "Product created successfully"));
    }

    @PostMapping("/api/v1/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createAdmin(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        Product product = productService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ProductDetailResponse.from(product), "Product created successfully"));
    }

    @GetMapping({"/api/v1/products/my", "/api/products/my"})
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> findMyProducts(
            @RequestAttribute("userId") Long userId
    ) {
        List<ProductSummaryResponse> list = productService.findBySellerId(userId)
                .stream()
                .map(ProductSummaryResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping({"/api/v1/products/seller/{sellerId}", "/api/products/seller/{sellerId}"})
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> findBySellerId(
            @PathVariable Long sellerId
    ) {
        List<ProductSummaryResponse> list = productService.findActiveProductsBySellerId(sellerId)
                .stream()
                .map(ProductSummaryResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PutMapping({"/api/v1/products/{id}", "/api/products/{id}"})
    public ResponseEntity<ApiResponse<ProductDetailResponse>> update(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            Authentication authentication,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        boolean isAdmin = isAdmin(authentication);
        Product product = productService.update(id, userId, isAdmin, request);
        return ResponseEntity.ok(ApiResponse.success(ProductDetailResponse.from(product), "Product updated successfully"));
    }

    @PutMapping("/api/v1/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateAdmin(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        Product product = productService.update(id, userId, true, request);
        return ResponseEntity.ok(ApiResponse.success(ProductDetailResponse.from(product), "Product updated successfully"));
    }

    @PatchMapping({"/api/v1/products/{id}/stock", "/api/products/{id}/stock"})
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateStock(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            Authentication authentication,
            @Valid @RequestBody ProductStockUpdateRequest request
    ) {
        boolean isAdmin = isAdmin(authentication);
        Product product = productService.updateStock(id, userId, isAdmin, request.stockQuantity());
        return ResponseEntity.ok(ApiResponse.success(ProductDetailResponse.from(product), "Product stock updated successfully"));
    }

    @DeleteMapping({"/api/v1/products/{id}", "/api/products/{id}"})
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            Authentication authentication
    ) {
        boolean isAdmin = isAdmin(authentication);
        productService.deactivate(id, userId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateAdmin(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId
    ) {
        productService.deactivate(id, userId, true);
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
