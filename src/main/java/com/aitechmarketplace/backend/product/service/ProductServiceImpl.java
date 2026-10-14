package com.aitechmarketplace.backend.product.service;

import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.repository.BrandRepository;
import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.common.util.SlugUtils;
import com.aitechmarketplace.backend.product.dto.*;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.product.specification.ProductSpecification;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public ProductServiceImpl(
            ProductRepository productRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository) {

        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Override
    public Product create(Long sellerId, ProductCreateRequest request) {
        Long actualSellerId = (sellerId != null) ? sellerId : request.sellerId();
        if (actualSellerId == null) {
            throw new BadRequestException("Seller is required", "SELLER_REQUIRED");
        }

        User seller = userRepository.findById(actualSellerId)
                .orElseThrow(() -> new NotFoundException("Seller not found", "USER_NOT_FOUND"));

        if (!seller.isActive()) {
            throw new IllegalStateException("Seller account is inactive");
        }

        Long categoryId = request.resolveCategoryId();
        if (categoryId == null) {
            throw new BadRequestException("Category is required", "CATEGORY_REQUIRED");
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category not found", "CATEGORY_NOT_FOUND"));

        Brand brand = null;
        if (request.brandId() != null) {
            brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new NotFoundException("Brand not found", "BRAND_NOT_FOUND"));
        }

        String sku = (request.sku() != null && !request.sku().isBlank())
                ? request.sku().trim()
                : "SKU-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);

        if (productRepository.existsBySku(sku)) {
            throw new ConflictException("Product SKU already exists", "SKU_ALREADY_EXISTS");
        }

        String slug;
        if (request.slug() != null && !request.slug().isBlank()) {
            slug = SlugUtils.toSlug(request.slug());
            if (productRepository.existsBySlug(slug)) {
                throw new ConflictException("Product slug already exists", "SLUG_ALREADY_EXISTS");
            }
        } else {
            String baseSlug = SlugUtils.toSlug(request.name());
            slug = baseSlug;
            if (productRepository.existsBySlug(slug)) {
                slug = baseSlug + "-" + System.currentTimeMillis();
            }
        }

        Map<String, Object> specs = request.specifications() != null ? request.specifications() : new HashMap<>();

        Product product = new Product();
        product.setSeller(seller);
        product.setName(request.name());
        product.setSku(sku);
        product.setSlug(slug);
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0);
        product.setCategory(category);
        product.setBrand(brand);
        product.setSpecifications(specs);
        product.setStatus("ACTIVE");

        return productRepository.save(product);
    }

    @Override
    public Product create(
            Long sellerId,
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            List<Long> categoryIds) {

        ProductCreateRequest request = new ProductCreateRequest(
                name, description, price, stockQuantity, categoryIds);
        return create(sellerId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findBySlug(String slug) {
        return productRepository.findBySlug(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findActiveProducts() {
        return productRepository.findByActiveTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findBySellerId(Long sellerId) {
        return productRepository.findBySellerId(sellerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findActiveProductsBySellerId(Long sellerId) {
        return productRepository.findBySellerIdAndActiveTrue(sellerId);
    }

    @Override
    public Product update(Long productId, Long userId, boolean isAdmin, ProductUpdateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found", "PRODUCT_NOT_FOUND"));

        if (!isAdmin && !product.getSeller().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this product", "FORBIDDEN");
        }

        if (request.name() != null && !request.name().isBlank()) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.stockQuantity() != null) {
            product.setStockQuantity(request.stockQuantity());
        }
        if (request.status() != null && !request.status().isBlank()) {
            product.setStatus(request.status());
        }
        if (request.specifications() != null) {
            product.setSpecifications(request.specifications());
        }

        Long categoryId = request.resolveCategoryId();
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException("Category not found", "CATEGORY_NOT_FOUND"));
            product.setCategory(category);
        }

        if (request.brandId() != null) {
            Brand brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new NotFoundException("Brand not found", "BRAND_NOT_FOUND"));
            product.setBrand(brand);
        }

        if (request.sku() != null && !request.sku().isBlank() && !request.sku().equals(product.getSku())) {
            if (productRepository.existsBySku(request.sku())) {
                throw new ConflictException("Product SKU already exists", "SKU_ALREADY_EXISTS");
            }
            product.setSku(request.sku());
        }

        if (request.slug() != null && !request.slug().isBlank()) {
            String newSlug = SlugUtils.toSlug(request.slug());
            if (!newSlug.equals(product.getSlug()) && productRepository.existsBySlug(newSlug)) {
                throw new ConflictException("Product slug already exists", "SLUG_ALREADY_EXISTS");
            }
            product.setSlug(newSlug);
        }

        return productRepository.save(product);
    }

    @Override
    public Product update(
            Long productId,
            Long userId,
            boolean isAdmin,
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            List<Long> categoryIds) {

        ProductUpdateRequest request = new ProductUpdateRequest(
                name, description, price, stockQuantity, categoryIds);
        return update(productId, userId, isAdmin, request);
    }

    @Override
    public void deactivate(
            Long productId,
            Long userId,
            boolean isAdmin) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found", "PRODUCT_NOT_FOUND"));

        if (!isAdmin && !product.getSeller().getId().equals(userId)) {
            throw new ForbiddenException("You are not allowed to deactivate this product", "FORBIDDEN");
        }

        product.setStatus("INACTIVE");
        productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductPageResponse search(
            ProductSearchRequest request,
            Pageable pageable) {

        Specification<Product> specification = ProductSpecification.isActive()
                .and(ProductSpecification.hasKeyword(request.keyword()))
                .and(ProductSpecification.hasCategoryId(request.categoryId()))
                .and(ProductSpecification.hasBrandId(request.brandId()))
                .and(ProductSpecification.priceGreaterThanOrEqual(request.minPrice()))
                .and(ProductSpecification.priceLessThanOrEqual(request.maxPrice()));

        Page<ProductSummaryResponse> result = productRepository
                .findAll(specification, pageable)
                .map(ProductSummaryResponse::from);

        return ProductPageResponse.from(result);
    }

    @Override
    public Product updateStock(
            Long productId,
            Long userId,
            boolean isAdmin,
            Integer stockQuantity) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found", "PRODUCT_NOT_FOUND"));

        if (!isAdmin && !product.getSeller().getId().equals(userId)) {
            throw new ForbiddenException("You are not allowed to update stock for this product", "FORBIDDEN");
        }

        product.setStockQuantity(stockQuantity);
        return productRepository.save(product);
    }
}
