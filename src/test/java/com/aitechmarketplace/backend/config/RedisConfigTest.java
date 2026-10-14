package com.aitechmarketplace.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;

import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisConfigTest {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    @Qualifier("checkoutLockRegistry")
    private RedisLockRegistry checkoutLockRegistry;

    @Test
    void redisConnectionFactory_shouldBeConfigured() {
        assertThat(redisConnectionFactory).isNotNull();
    }

    @Test
    void checkoutLockRegistry_shouldBeConfigured() {
        assertThat(checkoutLockRegistry).isNotNull();
        Lock lock = checkoutLockRegistry.obtain("test-key");
        assertThat(lock).isNotNull();
    }
}
