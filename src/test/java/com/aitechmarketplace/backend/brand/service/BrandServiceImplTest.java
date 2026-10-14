package com.aitechmarketplace.backend.brand.service;

import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.repository.BrandRepository;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandServiceImplTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private BrandServiceImpl brandService;

    @Test
    void create_shouldCreateBrandWithAutoSlug() {
        when(brandRepository.existsByName("ASUS")).thenReturn(false);
        when(brandRepository.existsBySlug("asus")).thenReturn(false);

        Brand savedBrand = new Brand();
        savedBrand.setId(1L);
        savedBrand.setName("ASUS");
        savedBrand.setSlug("asus");
        savedBrand.setStatus("ACTIVE");

        when(brandRepository.save(any(Brand.class))).thenReturn(savedBrand);

        Brand result = brandService.create("ASUS", null);

        assertNotNull(result);
        assertEquals("ASUS", result.getName());
        assertEquals("asus", result.getSlug());
        assertEquals("ACTIVE", result.getStatus());
        verify(brandRepository).save(any(Brand.class));
    }

    @Test
    void create_withExplicitSlug_shouldUseProvidedSlug() {
        when(brandRepository.existsByName("ASUS")).thenReturn(false);
        when(brandRepository.existsBySlug("asus-rog")).thenReturn(false);

        Brand savedBrand = new Brand();
        savedBrand.setId(1L);
        savedBrand.setName("ASUS");
        savedBrand.setSlug("asus-rog");
        savedBrand.setStatus("ACTIVE");

        when(brandRepository.save(any(Brand.class))).thenReturn(savedBrand);

        Brand result = brandService.create("ASUS", "asus-rog");

        assertNotNull(result);
        assertEquals("asus-rog", result.getSlug());
        verify(brandRepository).save(any(Brand.class));
    }

    @Test
    void create_withExistingName_shouldThrowConflictException() {
        when(brandRepository.existsByName("ASUS")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> brandService.create("ASUS", null));

        assertEquals("Brand name already exists", ex.getMessage());
        assertEquals("BRAND_ALREADY_EXISTS", ex.getErrorCode());
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    void create_withExistingSlug_shouldThrowConflictException() {
        when(brandRepository.existsByName("ASUS")).thenReturn(false);
        when(brandRepository.existsBySlug("asus")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> brandService.create("ASUS", "asus"));

        assertEquals("Brand slug already exists", ex.getMessage());
        assertEquals("SLUG_ALREADY_EXISTS", ex.getErrorCode());
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    void findById_shouldReturnBrandWhenFound() {
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setName("ASUS");

        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

        Optional<Brand> result = brandService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("ASUS", result.get().getName());
    }

    @Test
    void findBySlug_shouldReturnBrandWhenFound() {
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setSlug("asus");

        when(brandRepository.findBySlug("asus")).thenReturn(Optional.of(brand));

        Optional<Brand> result = brandService.findBySlug("asus");

        assertTrue(result.isPresent());
        assertEquals("asus", result.get().getSlug());
    }

    @Test
    void findActiveBrands_shouldReturnActiveBrands() {
        Brand b1 = new Brand();
        b1.setName("Apple");
        Brand b2 = new Brand();
        b2.setName("Dell");

        when(brandRepository.findActiveBrands()).thenReturn(List.of(b1, b2));

        List<Brand> result = brandService.findActiveBrands();

        assertEquals(2, result.size());
        verify(brandRepository).findActiveBrands();
    }

    @Test
    void update_shouldUpdateFields() {
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setName("Old Brand");
        brand.setSlug("old-brand");
        brand.setStatus("ACTIVE");

        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
        when(brandRepository.existsByName("New Brand")).thenReturn(false);
        when(brandRepository.existsBySlug("new-brand")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        Brand updated = brandService.update(1L, "New Brand", "new-brand", "ACTIVE");

        assertEquals("New Brand", updated.getName());
        assertEquals("new-brand", updated.getSlug());
    }

    @Test
    void update_notFound_shouldThrowNotFoundException() {
        when(brandRepository.findById(99L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> brandService.update(99L, "Name", "slug", "ACTIVE"));

        assertEquals("Brand not found", ex.getMessage());
        assertEquals("BRAND_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void deactivate_shouldSetStatusToInactive() {
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setStatus("ACTIVE");

        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
        when(productRepository.existsByBrandIdAndStatus(1L, "ACTIVE")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        brandService.deactivate(1L);

        assertEquals("INACTIVE", brand.getStatus());
        verify(brandRepository).save(brand);
    }

    @Test
    void deactivate_whenReferencedByActiveProducts_shouldThrowConflictException() {
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setStatus("ACTIVE");

        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
        when(productRepository.existsByBrandIdAndStatus(1L, "ACTIVE")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> brandService.deactivate(1L));

        assertEquals("Cannot deactivate brand currently in use by active products", ex.getMessage());
        assertEquals("RESOURCE_IN_USE", ex.getErrorCode());
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    void deactivate_notFound_shouldThrowNotFoundException() {
        when(brandRepository.findById(99L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> brandService.deactivate(99L));

        assertEquals("Brand not found", ex.getMessage());
        assertEquals("BRAND_NOT_FOUND", ex.getErrorCode());
    }
}
