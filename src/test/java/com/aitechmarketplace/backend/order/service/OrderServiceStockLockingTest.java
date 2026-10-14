package com.aitechmarketplace.backend.order.service;

import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.repository.BrandRepository;
import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.user.entity.Role;
import com.aitechmarketplace.backend.user.entity.RoleName;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.RoleRepository;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderServiceStockLockingTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Test
    @Transactional
    void findByIdForUpdate_acquiresLockAndReturnsProduct() {
        Role sellerRole = roleRepository.findByName(RoleName.SELLER).orElseThrow();

        User seller = new User();
        seller.setEmail("lock-seller-" + System.currentTimeMillis() + "@test.com");
        seller.setPasswordHash("hashed");
        seller.setFullName("Lock Seller");
        seller.setRoles(Set.of(sellerRole));
        seller = userRepository.save(seller);

        Category category = new Category();
        category.setName("Lock Cat " + System.currentTimeMillis());
        category.setSlug("lock-cat-" + System.currentTimeMillis());
        category = categoryRepository.save(category);

        Brand brand = new Brand();
        brand.setName("Lock Brand " + System.currentTimeMillis());
        brand.setSlug("lock-brand-" + System.currentTimeMillis());
        brand = brandRepository.save(brand);

        Product product = new Product();
        product.setName("Lock Product");
        product.setSlug("lock-product-" + System.currentTimeMillis());
        product.setSku("SKU-LOCK-" + System.currentTimeMillis());
        product.setPrice(new BigDecimal("100000.00"));
        product.setStockQuantity(50);
        product.setStatus("ACTIVE");
        product.setSeller(seller);
        product.setCategory(category);
        product.setBrand(brand);
        product = productRepository.save(product);

        Optional<Product> lockedProduct = productRepository.findByIdForUpdate(product.getId());
        assertThat(lockedProduct).isPresent();
        assertThat(lockedProduct.get().getId()).isEqualTo(product.getId());
        assertThat(lockedProduct.get().getStockQuantity()).isEqualTo(50);
    }
}
