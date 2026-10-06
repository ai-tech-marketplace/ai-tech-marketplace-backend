package com.aitechmarketplace.backend.product.image.controller;

import com.aitechmarketplace.backend.product.image.dto.ProductImageResponse;
import com.aitechmarketplace.backend.product.image.entity.ProductImage;
import com.aitechmarketplace.backend.product.image.service.ProductImageService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(
            ProductImageService productImageService
    ) {
        this.productImageService = productImageService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageResponse upload(
            @PathVariable Long productId,
            @RequestAttribute("userId") Long userId,
            Authentication authentication,
            @RequestPart("file") @NotNull MultipartFile file
    ) throws IOException {

        boolean isAdmin = isAdmin(authentication);

        ProductImage image = productImageService.upload(
                productId,
                userId,
                isAdmin,
                file
        );

        return ProductImageResponse.from(image);
    }

    @GetMapping
    public List<ProductImageResponse> findByProductId(
            @PathVariable Long productId
    ) {

        return productImageService.findByProductId(productId)
                .stream()
                .map(ProductImageResponse::from)
                .toList();
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @RequestAttribute("userId") Long userId,
            Authentication authentication
    ) {

        boolean isAdmin = isAdmin(authentication);

        productImageService.delete(
                productId,
                imageId,
                userId,
                isAdmin
        );
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
