package com.aitechmarketplace.backend.product.service;

import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.dto.ProductPageResponse;
import com.aitechmarketplace.backend.product.dto.ProductResponse;
import com.aitechmarketplace.backend.product.dto.ProductSearchRequest;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public ProductServiceImpl(
            ProductRepository productRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository) {

        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Product create(
            Long sellerId,
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            List<Long> categoryIds) {

        User seller = userRepository.findById(sellerId)
                .orElseThrow(() ->
                        new NotFoundException("Seller not found"));

        if (!seller.isActive()) {
            throw new IllegalStateException(
                    "Seller account is inactive");
        }

        Set<Category> categories =
                resolveCategories(categoryIds);

        Product product = new Product();

        product.setSeller(seller);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setStockQuantity(stockQuantity);
        product.getCategories().addAll(categories);

        return productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Product> findById(Long id) {
        return productRepository.findById(id);
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
    public List<Product> findActiveProductsBySellerId(
            Long sellerId) {

        return productRepository.findBySellerIdAndActiveTrue(
                sellerId);
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

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new NotFoundException("Product not found"));

        /*
         * USER:
         *   chỉ được sửa product của chính mình.
         *
         * ADMIN:
         *   được sửa product của bất kỳ seller nào.
         */
        if (!isAdmin &&
                !product.getSeller().getId().equals(userId)) {

            throw new ForbiddenException(
                    "You do not own this product"
            );
        }

        Set<Category> categories =
                resolveCategories(categoryIds);

        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setStockQuantity(stockQuantity);

        product.getCategories().clear();
        product.getCategories().addAll(categories);

        return productRepository.save(product);
    }

    @Override
    public void deactivate(
            Long productId,
            Long userId,
            boolean isAdmin) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new NotFoundException("Product not found"));

        /*
         * USER:
         *   chỉ được deactivate product của chính mình.
         *
         * ADMIN:
         *   được deactivate product của bất kỳ seller nào.
         */
        if (!isAdmin &&
                !product.getSeller().getId().equals(userId)) {

            throw new ForbiddenException(
                    "You are not allowed to deactivate this product"
            );
        }

        product.setActive(false);

        productRepository.save(product);
    }

    private Set<Category> resolveCategories(
            List<Long> categoryIds) {

        if (categoryIds == null ||
                categoryIds.isEmpty()) {

            return new HashSet<>();
        }

        List<Category> categories =
                categoryRepository.findAllById(categoryIds);

        if (categories.size() != categoryIds.size()) {

            throw new NotFoundException(
                    "One or more categories not found");
        }

        return new HashSet<>(categories);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductPageResponse search(
            ProductSearchRequest request,
            Pageable pageable) {

        Specification<Product> specification =
                ProductSpecification.isActive()
                        .and(
                                ProductSpecification.hasKeyword(
                                        request.keyword()
                                )
                        )
                        .and(
                                ProductSpecification.hasCategoryId(
                                        request.categoryId()
                                )
                        )
                        .and(
                                ProductSpecification.priceGreaterThanOrEqual(
                                        request.minPrice()
                                )
                        )
                        .and(
                                ProductSpecification.priceLessThanOrEqual(
                                        request.maxPrice()
                                )
                        );

        Page<ProductResponse> result =
                productRepository
                        .findAll(specification, pageable)
                        .map(ProductResponse::from);

        return ProductPageResponse.from(result);
    }

    @Override
public Product updateStock(
        Long productId,
        Long userId,
        boolean isAdmin,
        Integer stockQuantity) {

    Product product = productRepository.findById(productId)
            .orElseThrow(() ->
                    new NotFoundException("Product not found"));

    if (!isAdmin &&
            !product.getSeller().getId().equals(userId)) {

        throw new ForbiddenException(
                "You are not allowed to update stock for this product"
        );
    }

    product.setStockQuantity(stockQuantity);

    return productRepository.save(product);
}
}
