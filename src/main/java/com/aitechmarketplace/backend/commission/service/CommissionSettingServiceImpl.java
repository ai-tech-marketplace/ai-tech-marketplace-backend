package com.aitechmarketplace.backend.commission.service;

import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.commission.dto.CommissionSettingCreateRequest;
import com.aitechmarketplace.backend.commission.dto.CommissionSettingResponse;
import com.aitechmarketplace.backend.commission.dto.CommissionSettingUpdateRequest;
import com.aitechmarketplace.backend.commission.entity.CommissionScope;
import com.aitechmarketplace.backend.commission.entity.CommissionSetting;
import com.aitechmarketplace.backend.commission.repository.CommissionSettingRepository;
import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class CommissionSettingServiceImpl implements CommissionSettingService {

    public static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.0500");

    private final CommissionSettingRepository repository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CommissionSettingServiceImpl(
            CommissionSettingRepository repository,
            CategoryRepository categoryRepository,
            UserRepository userRepository
    ) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommissionSettingResponse> findAll(CommissionScope scope, Boolean isActive) {
        List<CommissionSetting> settings;
        if (scope != null && isActive != null) {
            settings = repository.findByScopeAndIsActive(scope, isActive);
        } else if (scope != null) {
            settings = repository.findByScope(scope);
        } else if (isActive != null) {
            settings = repository.findAllByIsActive(isActive);
        } else {
            settings = repository.findAll();
        }

        return settings.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CommissionSettingResponse findById(Long id) {
        CommissionSetting setting = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Commission setting not found with ID: " + id, "COMMISSION_NOT_FOUND"));
        return toResponse(setting);
    }

    @Override
    public CommissionSettingResponse create(CommissionSettingCreateRequest request) {
        validateScopeAndTarget(request.scope(), request.targetId());

        CommissionSetting setting = new CommissionSetting(
                request.scope(),
                request.targetId(),
                request.rate(),
                request.description(),
                request.isActive() != null ? request.isActive() : true
        );

        CommissionSetting saved = repository.save(setting);
        return toResponse(saved);
    }

    @Override
    public CommissionSettingResponse update(Long id, CommissionSettingUpdateRequest request) {
        CommissionSetting setting = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Commission setting not found with ID: " + id, "COMMISSION_NOT_FOUND"));

        setting.setRate(request.rate());
        setting.setDescription(request.description());
        if (request.isActive() != null) {
            setting.setIsActive(request.isActive());
        }

        CommissionSetting updated = repository.save(setting);
        return toResponse(updated);
    }

    @Override
    public void delete(Long id) {
        CommissionSetting setting = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Commission setting not found with ID: " + id, "COMMISSION_NOT_FOUND"));
        repository.delete(setting);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal resolveCommissionRate(Long sellerId, Long categoryId) {
        // Precedence 1: SELLER specific rule
        if (sellerId != null) {
            var sellerRule = repository.findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.SELLER, sellerId);
            if (sellerRule.isPresent()) {
                return sellerRule.get().getRate();
            }
        }

        // Precedence 2: CATEGORY specific rule
        if (categoryId != null) {
            var categoryRule = repository.findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.CATEGORY, categoryId);
            if (categoryRule.isPresent()) {
                return categoryRule.get().getRate();
            }
        }

        // Precedence 3: GLOBAL rule
        var globalRule = repository.findByScopeAndIsActiveTrue(CommissionScope.GLOBAL);
        if (globalRule.isPresent()) {
            return globalRule.get().getRate();
        }

        // Precedence 4: Hardcoded platform fallback (5.0%)
        return DEFAULT_COMMISSION_RATE;
    }

    private void validateScopeAndTarget(CommissionScope scope, Long targetId) {
        switch (scope) {
            case GLOBAL -> {
                if (targetId != null) {
                    throw new BadRequestException("Target ID must be null for GLOBAL scope", "COMMISSION_INVALID_SCOPE");
                }
                if (repository.existsByScopeAndTargetIdIsNull(CommissionScope.GLOBAL)) {
                    throw new ConflictException("Global commission setting already exists", "COMMISSION_DUPLICATE_SCOPE");
                }
            }
            case CATEGORY -> {
                if (targetId == null) {
                    throw new BadRequestException("Target ID is required for CATEGORY scope", "COMMISSION_INVALID_SCOPE");
                }
                if (!categoryRepository.existsById(targetId)) {
                    throw new NotFoundException("Category not found with ID: " + targetId, "COMMISSION_TARGET_NOT_FOUND");
                }
                if (repository.existsByScopeAndTargetId(CommissionScope.CATEGORY, targetId)) {
                    throw new ConflictException("Commission setting for this category already exists", "COMMISSION_DUPLICATE_SCOPE");
                }
            }
            case SELLER -> {
                if (targetId == null) {
                    throw new BadRequestException("Target ID is required for SELLER scope", "COMMISSION_INVALID_SCOPE");
                }
                if (!userRepository.existsById(targetId)) {
                    throw new NotFoundException("Seller not found with ID: " + targetId, "COMMISSION_TARGET_NOT_FOUND");
                }
                if (repository.existsByScopeAndTargetId(CommissionScope.SELLER, targetId)) {
                    throw new ConflictException("Commission setting for this seller already exists", "COMMISSION_DUPLICATE_SCOPE");
                }
            }
        }
    }

    private CommissionSettingResponse toResponse(CommissionSetting setting) {
        String targetName = resolveTargetName(setting.getScope(), setting.getTargetId());
        return new CommissionSettingResponse(
                setting.getId(),
                setting.getScope(),
                setting.getTargetId(),
                targetName,
                setting.getRate(),
                setting.getDescription(),
                setting.getIsActive(),
                setting.getCreatedAt(),
                setting.getUpdatedAt()
        );
    }

    private String resolveTargetName(CommissionScope scope, Long targetId) {
        if (targetId == null || scope == CommissionScope.GLOBAL) {
            return null;
        }
        if (scope == CommissionScope.CATEGORY) {
            return categoryRepository.findById(targetId)
                    .map(com.aitechmarketplace.backend.category.entity.Category::getName)
                    .orElse(null);
        }
        if (scope == CommissionScope.SELLER) {
            return userRepository.findById(targetId)
                    .map(com.aitechmarketplace.backend.user.entity.User::getFullName)
                    .orElse(null);
        }
        return null;
    }
}
