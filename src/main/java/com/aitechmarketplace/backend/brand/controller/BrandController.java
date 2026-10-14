package com.aitechmarketplace.backend.brand.controller;

import com.aitechmarketplace.backend.brand.dto.BrandCreateRequest;
import com.aitechmarketplace.backend.brand.dto.BrandResponse;
import com.aitechmarketplace.backend.brand.dto.BrandUpdateRequest;
import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.service.BrandService;
import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping("/api/v1/brands")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> findActiveBrands() {
        List<BrandResponse> data = brandService.findActiveBrands()
                .stream()
                .map(BrandResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/api/v1/brands/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> findById(@PathVariable Long id) {
        Brand brand = brandService.findById(id)
                .orElseThrow(() -> new NotFoundException("Brand not found", "BRAND_NOT_FOUND"));
        return ResponseEntity.ok(ApiResponse.success(BrandResponse.from(brand)));
    }

    @PostMapping("/api/v1/admin/brands")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> create(
            @Valid @RequestBody BrandCreateRequest request
    ) {
        Brand brand = brandService.create(
                request.name(),
                request.slug()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(BrandResponse.from(brand), "Brand created successfully"));
    }

    @PutMapping("/api/v1/admin/brands/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody BrandUpdateRequest request
    ) {
        Brand brand = brandService.update(
                id,
                request.name(),
                request.slug(),
                request.status()
        );
        return ResponseEntity.ok(ApiResponse.success(BrandResponse.from(brand), "Brand updated successfully"));
    }

    @DeleteMapping("/api/v1/admin/brands/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        brandService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
