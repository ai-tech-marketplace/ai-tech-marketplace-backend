package com.aitechmarketplace.backend.brand.service;

import com.aitechmarketplace.backend.brand.entity.Brand;

import java.util.List;
import java.util.Optional;

public interface BrandService {

    Brand create(String name, String slug);

    default Brand create(String name) {
        return create(name, null);
    }

    Optional<Brand> findById(Long id);

    Optional<Brand> findBySlug(String slug);

    List<Brand> findActiveBrands();

    Brand update(Long brandId, String name, String slug, String status);

    default Brand update(Long brandId, String name, String slug) {
        return update(brandId, name, slug, null);
    }

    void deactivate(Long brandId);
}
