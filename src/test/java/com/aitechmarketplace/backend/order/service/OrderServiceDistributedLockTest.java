package com.aitechmarketplace.backend.order.service;

import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.order.dto.OrderCreateRequest;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.integration.support.locks.DistributedLock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class OrderServiceDistributedLockTest {

    @Autowired
    private OrderService orderService;

    @MockitoBean(name = "checkoutLockRegistry")
    private RedisLockRegistry checkoutLockRegistry;

    @Test
    void createOrder_throwsConflict_whenLockNotAcquired() throws Exception {
        DistributedLock mockLock = mock(DistributedLock.class);
        when(checkoutLockRegistry.obtain("user:1")).thenReturn(mockLock);
        when(mockLock.tryLock(5, TimeUnit.SECONDS)).thenReturn(false);

        OrderCreateRequest request = new OrderCreateRequest(10L, PaymentMethod.COD, null, "Test notes");

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Checkout already in progress");
    }

    @Test
    void createOrder_fails_whenRedisUnavailable() {
        when(checkoutLockRegistry.obtain(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis connection refused"));

        OrderCreateRequest request = new OrderCreateRequest(10L, PaymentMethod.COD, null, "Test notes");

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}
