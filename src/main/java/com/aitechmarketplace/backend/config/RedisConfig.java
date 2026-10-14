package com.aitechmarketplace.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;

@Configuration
public class RedisConfig {

    private static final long DEFAULT_LOCK_EXPIRATION_MS = 30_000L;

    @Bean("checkoutLockRegistry")
    public RedisLockRegistry checkoutLockRegistry(RedisConnectionFactory connectionFactory) {
        return new RedisLockRegistry(connectionFactory, "checkout", DEFAULT_LOCK_EXPIRATION_MS);
    }
}
