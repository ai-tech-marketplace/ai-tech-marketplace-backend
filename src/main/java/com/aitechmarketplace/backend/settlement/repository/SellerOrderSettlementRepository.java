package com.aitechmarketplace.backend.settlement.repository;

import com.aitechmarketplace.backend.settlement.entity.SellerOrderSettlement;
import com.aitechmarketplace.backend.settlement.entity.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SellerOrderSettlementRepository extends JpaRepository<SellerOrderSettlement, Long> {

    List<SellerOrderSettlement> findByOrderId(Long orderId);

    List<SellerOrderSettlement> findBySellerId(Long sellerId);

    Page<SellerOrderSettlement> findBySellerIdAndStatus(Long sellerId, SettlementStatus status, Pageable pageable);
}
