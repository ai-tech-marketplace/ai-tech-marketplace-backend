package com.aitechmarketplace.backend.address.repository;

import com.aitechmarketplace.backend.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByIdDesc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    int countByUserId(Long userId);

    Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);
}
