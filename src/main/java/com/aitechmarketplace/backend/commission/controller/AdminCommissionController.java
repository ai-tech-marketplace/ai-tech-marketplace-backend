package com.aitechmarketplace.backend.commission.controller;

import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.commission.dto.CommissionSettingCreateRequest;
import com.aitechmarketplace.backend.commission.dto.CommissionSettingResponse;
import com.aitechmarketplace.backend.commission.dto.CommissionSettingUpdateRequest;
import com.aitechmarketplace.backend.commission.entity.CommissionScope;
import com.aitechmarketplace.backend.commission.service.CommissionSettingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/commission-settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommissionController {

    private final CommissionSettingService commissionSettingService;

    public AdminCommissionController(CommissionSettingService commissionSettingService) {
        this.commissionSettingService = commissionSettingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CommissionSettingResponse>>> getAll(
            @RequestParam(required = false) CommissionScope scope,
            @RequestParam(required = false) Boolean isActive
    ) {
        List<CommissionSettingResponse> settings = commissionSettingService.findAll(scope, isActive);
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CommissionSettingResponse>> getById(@PathVariable Long id) {
        CommissionSettingResponse setting = commissionSettingService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(setting));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommissionSettingResponse>> create(
            @Valid @RequestBody CommissionSettingCreateRequest request
    ) {
        CommissionSettingResponse created = commissionSettingService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CommissionSettingResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CommissionSettingUpdateRequest request
    ) {
        CommissionSettingResponse updated = commissionSettingService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(@PathVariable Long id) {
        commissionSettingService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Commission configuration successfully removed")));
    }
}
