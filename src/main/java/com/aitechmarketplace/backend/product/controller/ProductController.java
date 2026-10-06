package com.aitechmarketplace.backend.product.controller;

import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.dto.ProductCreateRequest;
import com.aitechmarketplace.backend.product.dto.ProductPageResponse;
import com.aitechmarketplace.backend.product.dto.ProductResponse;
import com.aitechmarketplace.backend.product.dto.ProductSearchRequest;
import com.aitechmarketplace.backend.product.dto.ProductStockUpdateRequest;
import com.aitechmarketplace.backend.product.dto.ProductUpdateRequest;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.service.ProductService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody ProductCreateRequest request
    ) {

        Product product = productService.create(
                userId,
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                request.categoryIds()
        );

        return ProductResponse.from(product);
    }

    @GetMapping
    public ProductPageResponse search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {

        ProductSearchRequest request =
                new ProductSearchRequest(
                        keyword,
                        categoryId,
                        minPrice,
                        maxPrice
                );

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt"
                )
        );

        return productService.search(
                request,
                pageable
        );
    }

    @GetMapping("/my")
    public List<ProductResponse> findMyProducts(
            @RequestAttribute("userId") Long userId
    ) {

        return productService.findBySellerId(userId)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse findById(
            @PathVariable Long id
    ) {

        Product product = productService.findById(id)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Product not found"
                        )
                );

        return ProductResponse.from(product);
    }

    @GetMapping("/seller/{sellerId}")
    public List<ProductResponse> findBySellerId(
            @PathVariable Long sellerId
    ) {

        return productService
                .findActiveProductsBySellerId(sellerId)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            Authentication authentication,
            @Valid @RequestBody ProductUpdateRequest request
    ) {

        boolean isAdmin =
                authentication.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority.getAuthority()
                                        .equals("ROLE_ADMIN")
                        );

        Product product = productService.update(
                id,
                userId,
                isAdmin,
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                request.categoryIds()
        );

        return ProductResponse.from(product);
    }

    @PatchMapping("/{id}/stock")
public ProductResponse updateStock(
        @PathVariable Long id,
        @RequestAttribute("userId") Long userId,
        Authentication authentication,
        @Valid @RequestBody ProductStockUpdateRequest request
) {

    boolean isAdmin =
            authentication.getAuthorities()
                    .stream()
                    .anyMatch(authority ->
                            authority.getAuthority()
                                    .equals("ROLE_ADMIN")
                    );

    Product product = productService.updateStock(
            id,
            userId,
            isAdmin,
            request.stockQuantity()
    );

    return ProductResponse.from(product);
}

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            Authentication authentication
    ) {

        boolean isAdmin =
                authentication.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority.getAuthority()
                                        .equals("ROLE_ADMIN")
                        );

        productService.deactivate(
                id,
                userId,
                isAdmin
        );
    }
}
