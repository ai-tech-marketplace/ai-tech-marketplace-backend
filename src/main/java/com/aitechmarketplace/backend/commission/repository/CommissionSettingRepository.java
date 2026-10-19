package com.aitechmarketplace.backend.commission.repository;

import com.aitechmarketplace.backend.commission.entity.CommissionScope;
import com.aitechmarketplace.backend.commission.entity.CommissionSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionSettingRepository extends JpaRepository<CommissionSetting, Long> {

    Optional<CommissionSetting> findByScopeAndTargetId(CommissionScope scope, Long targetId);

    Optional<CommissionSetting> findByScopeAndTargetIdAndIsActiveTrue(CommissionScope scope, Long targetId);

    Optional<CommissionSetting> findByScopeAndIsActiveTrue(CommissionScope scope);

    List<CommissionSetting> findAllByIsActive(Boolean isActive);

    List<CommissionSetting> findByScope(CommissionScope scope);

    List<CommissionSetting> findByScopeAndIsActive(CommissionScope scope, Boolean isActive);

    boolean existsByScopeAndTargetId(CommissionScope scope, Long targetId);

    boolean existsByScopeAndTargetIdIsNull(CommissionScope scope);
}
