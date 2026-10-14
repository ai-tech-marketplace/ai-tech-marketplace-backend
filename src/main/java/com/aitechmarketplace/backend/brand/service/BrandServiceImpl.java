package com.aitechmarketplace.backend.brand.service;

import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.repository.BrandRepository;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.common.util.SlugUtils;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public BrandServiceImpl(
            BrandRepository brandRepository,
            ProductRepository productRepository
    ) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
    }

    @Override
    public Brand create(String name, String slug) {
        if (brandRepository.existsByName(name)) {
            throw new ConflictException(
                "Brand name already exists",
                "BRAND_ALREADY_EXISTS"
            );
        }

        String finalSlug = (slug != null && !slug.isBlank())
                ? SlugUtils.toSlug(slug)
                : SlugUtils.toSlug(name);

        if (brandRepository.existsBySlug(finalSlug)) {
            throw new ConflictException(
                "Brand slug already exists",
                "SLUG_ALREADY_EXISTS"
            );
        }

        Brand brand = new Brand();
        brand.setName(name);
        brand.setSlug(finalSlug);
        brand.setStatus("ACTIVE");

        return brandRepository.save(brand);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Brand> findById(Long id) {
        return brandRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Brand> findBySlug(String slug) {
        return brandRepository.findBySlug(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Brand> findActiveBrands() {
        return brandRepository.findActiveBrands();
    }

    @Override
    public Brand update(
            Long brandId,
            String name,
            String slug,
            String status
    ) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() ->
                new NotFoundException("Brand not found", "BRAND_NOT_FOUND")
            );

        if (name != null && !name.isBlank()) {
            if (!brand.getName().equalsIgnoreCase(name) && brandRepository.existsByName(name)) {
                throw new ConflictException(
                    "Brand name already exists",
                    "BRAND_ALREADY_EXISTS"
                );
            }
            brand.setName(name);
        }

        String finalSlug = (slug != null && !slug.isBlank())
                ? SlugUtils.toSlug(slug)
                : (name != null ? SlugUtils.toSlug(name) : brand.getSlug());

        if (!brand.getSlug().equals(finalSlug) && brandRepository.existsBySlug(finalSlug)) {
            throw new ConflictException(
                "Brand slug already exists",
                "SLUG_ALREADY_EXISTS"
            );
        }
        brand.setSlug(finalSlug);

        if (status != null && !status.isBlank()) {
            brand.setStatus(status);
        }

        return brandRepository.save(brand);
    }

    @Override
    public void deactivate(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() ->
                new NotFoundException("Brand not found", "BRAND_NOT_FOUND")
            );

        boolean hasActiveProducts = productRepository.existsByBrandIdAndStatus(brandId, "ACTIVE");
        if (hasActiveProducts) {
            throw new ConflictException(
                "Cannot deactivate brand currently in use by active products",
                "RESOURCE_IN_USE"
            );
        }

        brand.setStatus("INACTIVE");
        brandRepository.save(brand);
    }
}
