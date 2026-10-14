package com.aitechmarketplace.backend.product.image.controller;

import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.product.image.dto.ProductImageResponse;
import com.aitechmarketplace.backend.product.image.entity.ProductImage;
import com.aitechmarketplace.backend.product.image.service.ProductImageService;
import jakarta.validation.constraints.NotNull;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/products/{productId}/images", "/api/products/{productId}/images"})
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(
            ProductImageService productImageService
    ) {
        this.productImageService = productImageService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProductImageResponse>> upload(
            @PathVariable Long productId,
            @RequestAttribute("userId") Long userId,
            Authentication authentication,
            @RequestPart("file") @NotNull MultipartFile file,
            @RequestParam(value = "isPrimary", required = false, defaultValue = "false") boolean isPrimary
    ) throws IOException {

        boolean isAdmin = isAdmin(authentication);

        ProductImage image = productImageService.upload(
                productId,
                userId,
                isAdmin,
                file,
                isPrimary
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ProductImageResponse.from(image), "Image uploaded successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> findByProductId(
            @PathVariable Long productId
    ) {

        List<ProductImageResponse> images = productImageService.findByProductId(productId)
                .stream()
                .map(ProductImageResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(images));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(
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

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{imageId}/content")
    public ResponseEntity<byte[]> download(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        ResponseBytes<GetObjectResponse> object =
                productImageService.download(
                        productId,
                        imageId
                );

        GetObjectResponse response = object.response();

        MediaType mediaType = MediaType.parseMediaType(
                response.contentType()
        );

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(response.contentLength())
                .body(object.asByteArray());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
