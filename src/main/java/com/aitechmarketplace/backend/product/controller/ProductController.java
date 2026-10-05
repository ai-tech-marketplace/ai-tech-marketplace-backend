package com.aitechmarketplace.backend.product.controller;

import com.aitechmarketplace.backend.product.dto.ProductCreateRequest;
import com.aitechmarketplace.backend.product.dto.ProductResponse;
import com.aitechmarketplace.backend.product.dto.ProductUpdateRequest;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.service.ProductService;
import com.aitechmarketplace.backend.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(
        ProductService productService
    ) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
        @AuthenticationPrincipal User currentUser,
        @Valid @RequestBody ProductCreateRequest request
    ) {
        Product product = productService.create(
            currentUser.getId(),
            request.name(),
            request.description(),
            request.price(),
            request.stockQuantity()
        );

        return ProductResponse.from(product);
    }

    @GetMapping
    public List<ProductResponse> findActiveProducts() {
        return productService.findActiveProducts()
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
                new com.aitechmarketplace.backend.common.exception.NotFoundException(
                    "Product not found"
                )
            );

        return ProductResponse.from(product);
    }

    @GetMapping("/my")
    public List<ProductResponse> findMyProducts(
        @AuthenticationPrincipal User currentUser
    ) {
        return productService.findBySellerId(currentUser.getId())
            .stream()
            .map(ProductResponse::from)
            .toList();
    }

    @PutMapping("/{id}")
    public ProductResponse update(
        @PathVariable Long id,
        @AuthenticationPrincipal User currentUser,
        @Valid @RequestBody ProductUpdateRequest request
    ) {
        Product product = productService.update(
            id,
            currentUser.getId(),
            request.name(),
            request.description(),
            request.price(),
            request.stockQuantity()
        );

        return ProductResponse.from(product);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
        @PathVariable Long id,
        @AuthenticationPrincipal User currentUser
    ) {
        productService.deactivate(
            id,
            currentUser.getId()
        );
    }
}
