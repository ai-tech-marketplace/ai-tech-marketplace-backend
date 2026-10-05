package com.aitechmarketplace.backend.product.service;

import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductServiceImpl(
        ProductRepository productRepository,
        UserRepository userRepository
    ) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Product create(
        Long sellerId,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity
    ) {
        User seller = userRepository.findById(sellerId)
            .orElseThrow(() ->
                new NotFoundException("Seller not found")
            );

        if (!seller.isActive()) {
            throw new IllegalStateException(
                "Seller account is inactive"
            );
        }

        Product product = new Product();

        product.setSeller(seller);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setStockQuantity(stockQuantity);

        return productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
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
    public Product update(
        Long productId,
        Long sellerId,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity
    ) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() ->
                new NotFoundException("Product not found")
            );

        verifyOwnership(product, sellerId);

        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setStockQuantity(stockQuantity);

        return productRepository.save(product);
    }

    @Override
    public void deactivate(
        Long productId,
        Long sellerId
    ) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() ->
                new NotFoundException("Product not found")
            );

        verifyOwnership(product, sellerId);

        product.setActive(false);

        productRepository.save(product);
    }

    private void verifyOwnership(
        Product product,
        Long sellerId
    ) {
        if (
            product.getSeller() == null ||
            product.getSeller().getId() == null ||
            !product.getSeller().getId().equals(sellerId)
        ) {
            throw new ForbiddenException(
                "You do not own this product"
            );
        }
    }
}
