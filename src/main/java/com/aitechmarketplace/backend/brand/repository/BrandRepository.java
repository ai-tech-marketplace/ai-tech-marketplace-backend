package com.aitechmarketplace.backend.brand.repository;

import com.aitechmarketplace.backend.brand.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    @Query("SELECT b FROM Brand b WHERE b.status = 'ACTIVE' ORDER BY b.name ASC")
    List<Brand> findActiveBrands();

    List<Brand> findByStatus(String status);

    Optional<Brand> findBySlug(String slug);

    boolean existsByName(String name);

    boolean existsBySlug(String slug);
}
