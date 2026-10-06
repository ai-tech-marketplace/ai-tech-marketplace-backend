package com.aitechmarketplace.backend.product.controller;

import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.dto.ProductCreateRequest;
import com.aitechmarketplace.backend.product.dto.ProductResponse;
import com.aitechmarketplace.backend.product.dto.ProductUpdateRequest;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
            @Valid @RequestBody ProductCreateRequest request) {
        Product product = productService.create(
                userId,
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                request.categoryIds());

        return ProductResponse.from(product);
    }

    @GetMapping
    public List<ProductResponse> findActiveProducts() {
        return productService.findActiveProducts()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GetMapping("/my")
    public List<ProductResponse> findMyProducts(
            @RequestAttribute("userId") Long userId) {
        return productService.findBySellerId(userId)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse findById(
            @PathVariable Long id) {
        Product product = productService.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        return ProductResponse.from(product);
    }

    @GetMapping("/seller/{sellerId}")
    public List<ProductResponse> findBySellerId(
            @PathVariable Long sellerId) {
        return productService.findActiveProductsBySellerId(sellerId)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody ProductUpdateRequest request) {
        Product product = productService.update(
                id,
                userId,
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                request.categoryIds());

        return ProductResponse.from(product);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId) {
        productService.deactivate(id, userId);
    }

}
