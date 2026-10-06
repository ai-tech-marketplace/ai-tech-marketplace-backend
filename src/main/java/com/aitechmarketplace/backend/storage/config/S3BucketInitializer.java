package com.aitechmarketplace.backend.storage.config;

import com.aitechmarketplace.backend.storage.service.S3StorageService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3BucketInitializer {

    @Bean
    public ApplicationRunner initializeS3Bucket(
            S3StorageService storageService
    ) {
        return args -> storageService.createBucketIfNotExists();
    }
}
