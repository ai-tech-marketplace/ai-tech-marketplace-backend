package com.aitechmarketplace.backend.commission.service;

import com.aitechmarketplace.backend.commission.dto.CommissionSettingCreateRequest;
import com.aitechmarketplace.backend.commission.dto.CommissionSettingResponse;
import com.aitechmarketplace.backend.commission.dto.CommissionSettingUpdateRequest;
import com.aitechmarketplace.backend.commission.entity.CommissionScope;

import java.math.BigDecimal;
import java.util.List;

public interface CommissionSettingService {

    List<CommissionSettingResponse> findAll(CommissionScope scope, Boolean isActive);

    CommissionSettingResponse findById(Long id);

    CommissionSettingResponse create(CommissionSettingCreateRequest request);

    CommissionSettingResponse update(Long id, CommissionSettingUpdateRequest request);

    void delete(Long id);

    BigDecimal resolveCommissionRate(Long sellerId, Long categoryId);
}
