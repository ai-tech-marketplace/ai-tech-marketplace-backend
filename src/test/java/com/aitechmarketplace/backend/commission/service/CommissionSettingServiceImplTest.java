package com.aitechmarketplace.backend.commission.service;

import com.aitechmarketplace.backend.category.entity.Category;
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
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionSettingServiceImplTest {

    @Mock
    private CommissionSettingRepository repository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommissionSettingServiceImpl service;

    private User seller;
    private Category category;

    @BeforeEach
    void setUp() {
        seller = new User();
        seller.setId(10L);
        seller.setFullName("Seller Store");

        category = new Category();
        category.setId(20L);
        category.setName("Electronics");
    }

    @Test
    void resolveCommissionRate_shouldReturnSellerRate_whenSellerSettingExists() {
        CommissionSetting sellerSetting = new CommissionSetting(CommissionScope.SELLER, 10L, new BigDecimal("0.0200"), "Seller rate", true);
        when(repository.findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.SELLER, 10L))
                .thenReturn(Optional.of(sellerSetting));

        BigDecimal rate = service.resolveCommissionRate(10L, 20L);

        assertThat(rate).isEqualByComparingTo("0.0200");
        verify(repository, never()).findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.CATEGORY, 20L);
    }

    @Test
    void resolveCommissionRate_shouldReturnCategoryRate_whenSellerSettingAbsent() {
        when(repository.findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.SELLER, 10L))
                .thenReturn(Optional.empty());
        CommissionSetting categorySetting = new CommissionSetting(CommissionScope.CATEGORY, 20L, new BigDecimal("0.0350"), "Cat rate", true);
        when(repository.findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.CATEGORY, 20L))
                .thenReturn(Optional.of(categorySetting));

        BigDecimal rate = service.resolveCommissionRate(10L, 20L);

        assertThat(rate).isEqualByComparingTo("0.0350");
        verify(repository, never()).findByScopeAndIsActiveTrue(CommissionScope.GLOBAL);
    }

    @Test
    void resolveCommissionRate_shouldReturnGlobalRate_whenSellerAndCategoryAbsent() {
        when(repository.findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.SELLER, 10L))
                .thenReturn(Optional.empty());
        when(repository.findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.CATEGORY, 20L))
                .thenReturn(Optional.empty());
        CommissionSetting globalSetting = new CommissionSetting(CommissionScope.GLOBAL, null, new BigDecimal("0.0400"), "Global rate", true);
        when(repository.findByScopeAndIsActiveTrue(CommissionScope.GLOBAL))
                .thenReturn(Optional.of(globalSetting));

        BigDecimal rate = service.resolveCommissionRate(10L, 20L);

        assertThat(rate).isEqualByComparingTo("0.0400");
    }

    @Test
    void resolveCommissionRate_shouldReturnFallback_whenNoRulesMatch() {
        when(repository.findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.SELLER, 10L))
                .thenReturn(Optional.empty());
        when(repository.findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.CATEGORY, 20L))
                .thenReturn(Optional.empty());
        when(repository.findByScopeAndIsActiveTrue(CommissionScope.GLOBAL))
                .thenReturn(Optional.empty());

        BigDecimal rate = service.resolveCommissionRate(10L, 20L);

        assertThat(rate).isEqualByComparingTo("0.0500");
    }

    @Test
    void create_shouldPersistAndReturnSellerCommission() {
        CommissionSettingCreateRequest request = new CommissionSettingCreateRequest(
                CommissionScope.SELLER, 10L, new BigDecimal("0.0300"), "Custom seller rate", true
        );
        when(repository.existsByScopeAndTargetId(CommissionScope.SELLER, 10L)).thenReturn(false);
        when(userRepository.existsById(10L)).thenReturn(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(seller));
        when(repository.save(any(CommissionSetting.class))).thenAnswer(invocation -> {
            CommissionSetting s = invocation.getArgument(0);
            s.setId(1L);
            return s;
        });

        CommissionSettingResponse response = service.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.scope()).isEqualTo(CommissionScope.SELLER);
        assertThat(response.targetId()).isEqualTo(10L);
        assertThat(response.targetName()).isEqualTo("Seller Store");
        assertThat(response.rate()).isEqualByComparingTo("0.0300");
    }

    @Test
    void create_shouldThrowConflict_whenScopeAndTargetAlreadyExists() {
        CommissionSettingCreateRequest request = new CommissionSettingCreateRequest(
                CommissionScope.CATEGORY, 20L, new BigDecimal("0.0300"), "Cat rate", true
        );
        when(categoryRepository.existsById(20L)).thenReturn(true);
        when(repository.existsByScopeAndTargetId(CommissionScope.CATEGORY, 20L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COMMISSION_DUPLICATE_SCOPE");
    }

    @Test
    void create_shouldThrowNotFound_whenCategoryDoesNotExist() {
        CommissionSettingCreateRequest request = new CommissionSettingCreateRequest(
                CommissionScope.CATEGORY, 999L, new BigDecimal("0.0300"), "Cat rate", true
        );
        when(categoryRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COMMISSION_TARGET_NOT_FOUND");
    }

    @Test
    void create_shouldThrowBadRequest_whenGlobalHasTargetId() {
        CommissionSettingCreateRequest request = new CommissionSettingCreateRequest(
                CommissionScope.GLOBAL, 10L, new BigDecimal("0.0500"), "Invalid global", true
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COMMISSION_INVALID_SCOPE");
    }

    @Test
    void update_shouldModifyFieldsAndReturnUpdated() {
        CommissionSetting setting = new CommissionSetting(CommissionScope.CATEGORY, 20L, new BigDecimal("0.0300"), "Old", true);
        setting.setId(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(setting));
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(category));
        when(repository.save(any(CommissionSetting.class))).thenReturn(setting);

        CommissionSettingUpdateRequest updateRequest = new CommissionSettingUpdateRequest(
                new BigDecimal("0.0400"), "Updated", false
        );
        CommissionSettingResponse response = service.update(5L, updateRequest);

        assertThat(response.rate()).isEqualByComparingTo("0.0400");
        assertThat(response.description()).isEqualTo("Updated");
        assertThat(response.isActive()).isFalse();
        assertThat(response.targetName()).isEqualTo("Electronics");
    }

    @Test
    void update_shouldThrowNotFound_whenIdDoesNotExist() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        CommissionSettingUpdateRequest updateRequest = new CommissionSettingUpdateRequest(
                new BigDecimal("0.0400"), "Updated", true
        );

        assertThatThrownBy(() -> service.update(999L, updateRequest))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COMMISSION_NOT_FOUND");
    }

    @Test
    void delete_shouldRemove_whenExists() {
        CommissionSetting setting = new CommissionSetting(CommissionScope.GLOBAL, null, new BigDecimal("0.0500"), "Global", true);
        setting.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(setting));

        service.delete(1L);

        verify(repository).delete(setting);
    }

    @Test
    void delete_shouldThrowNotFound_whenMissing() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COMMISSION_NOT_FOUND");
    }
}
