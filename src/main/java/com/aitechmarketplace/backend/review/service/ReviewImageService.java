package com.aitechmarketplace.backend.review.service;

import com.aitechmarketplace.backend.review.dto.ReviewImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ReviewImageService {
    ReviewImageUploadResponse upload(MultipartFile file) throws IOException;
}
