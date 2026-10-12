package com.aitechmarketplace.backend.product.image.service;

import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.image.entity.ProductImage;
import com.aitechmarketplace.backend.product.image.repository.ProductImageRepository;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.storage.service.S3StorageService;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final S3StorageService storageService;

    public ProductImageServiceImpl(
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            S3StorageService storageService
    ) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.storageService = storageService;
    }

    @Override
    public ProductImage upload(
            Long productId,
            Long userId,
            boolean isAdmin,
            MultipartFile file
    ) throws IOException {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new NotFoundException("Product not found"));

        checkOwnership(product, userId, isAdmin);

        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long fileSize = file.getSize();

        String extension = extractExtension(originalFilename);

        String objectKey =
                "products/" +
                productId +
                "/" +
                UUID.randomUUID() +
                extension;

        storageService.upload(
                objectKey,
                file.getInputStream(),
                fileSize,
                contentType
        );

        try {
            ProductImage image = new ProductImage();

            image.setProduct(product);
            image.setObjectKey(objectKey);
            image.setOriginalFilename(
                    originalFilename != null
                            ? originalFilename
                            : "unknown"
            );
            image.setContentType(contentType);
            image.setFileSize(fileSize);

            int displayOrder =
                    productImageRepository.countByProductId(productId);

            image.setDisplayOrder(displayOrder);

            return productImageRepository.save(image);

        } catch (RuntimeException e) {
            storageService.delete(objectKey);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImage> findByProductId(Long productId) {

        if (!productRepository.existsById(productId)) {
            throw new NotFoundException("Product not found");
        }

        return productImageRepository
                .findByProductIdOrderByDisplayOrderAscIdAsc(productId);
    }

    @Override
    public void delete(
            Long productId,
            Long imageId,
            Long userId,
            boolean isAdmin
    ) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new NotFoundException("Product not found"));

        checkOwnership(product, userId, isAdmin);

        ProductImage image =
                productImageRepository
                        .findByIdAndProductId(imageId, productId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Product image not found"
                                ));

        storageService.delete(image.getObjectKey());

        productImageRepository.delete(image);
    }

    @Override
@Transactional(readOnly = true)
public ResponseBytes<GetObjectResponse> download(
        Long productId,
        Long imageId
) {
    if (!productRepository.existsById(productId)) {
        throw new NotFoundException("Product not found");
    }

    ProductImage image = productImageRepository
            .findByIdAndProductId(imageId, productId)
            .orElseThrow(() ->
                    new NotFoundException(
                            "Product image not found"
                    ));

    return storageService.download(image.getObjectKey());
}

    private void checkOwnership(
            Product product,
            Long userId,
            boolean isAdmin
    ) {

        if (!isAdmin &&
                !product.getSeller().getId().equals(userId)) {

            throw new ForbiddenException(
                    "You are not allowed to manage images for this product"
            );
        }
    }

    private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
        throw new BadRequestException(
                "Image file is required"
        );
    }

    String contentType = file.getContentType();

    if (contentType == null ||
            !contentType.startsWith("image/")) {
        throw new BadRequestException(
                "Only image files are allowed"
        );
    }
}

    private String extractExtension(String filename) {

        if (filename == null || filename.isBlank()) {
            return "";
        }

        int lastDot = filename.lastIndexOf('.');

        if (lastDot < 0) {
            return "";
        }

        return filename.substring(lastDot).toLowerCase();
    }
}
