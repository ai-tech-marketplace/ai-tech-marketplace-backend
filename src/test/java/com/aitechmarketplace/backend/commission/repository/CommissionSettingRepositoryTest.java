package com.aitechmarketplace.backend.commission.repository;

import com.aitechmarketplace.backend.commission.entity.CommissionScope;
import com.aitechmarketplace.backend.commission.entity.CommissionSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CommissionSettingRepositoryTest {

    @Autowired
    private CommissionSettingRepository repository;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void shouldSaveAndQueryCommissionSetting() {
        CommissionSetting setting = new CommissionSetting(CommissionScope.GLOBAL, null, new BigDecimal("0.0500"), "Global rate");
        CommissionSetting saved = repository.save(setting);

        assertThat(saved.getId()).isNotNull();
        Optional<CommissionSetting> found = repository.findByScopeAndIsActiveTrue(CommissionScope.GLOBAL);
        assertThat(found).isPresent();
        assertThat(found.get().getRate()).isEqualByComparingTo("0.0500");
    }

    @Test
    void shouldFindActiveCategoryCommission() {
        CommissionSetting setting = new CommissionSetting(CommissionScope.CATEGORY, 1L, new BigDecimal("0.0250"), "Laptops");
        repository.save(setting);

        Optional<CommissionSetting> found = repository.findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.CATEGORY, 1L);
        assertThat(found).isPresent();
        assertThat(found.get().getRate()).isEqualByComparingTo("0.0250");

        Optional<CommissionSetting> notFound = repository.findByScopeAndTargetIdAndIsActiveTrue(CommissionScope.CATEGORY, 999L);
        assertThat(notFound).isEmpty();
    }

    @Test
    void shouldEnforceUniqueScopeAndTargetConstraint() {
        CommissionSetting setting1 = new CommissionSetting(CommissionScope.SELLER, 10L, new BigDecimal("0.0200"), "Seller 10");
        repository.save(setting1);

        CommissionSetting setting2 = new CommissionSetting(CommissionScope.SELLER, 10L, new BigDecimal("0.0300"), "Duplicate Seller 10");
        assertThatThrownBy(() -> repository.saveAndFlush(setting2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
