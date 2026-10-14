package com.aitechmarketplace.backend.order.service;

import com.aitechmarketplace.backend.order.dto.OrderCreateRequest;
import com.aitechmarketplace.backend.order.dto.OrderResponse;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.locks.DistributedLock;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
class CheckoutLockBoundaryTest {

    @Autowired
    private OrderService orderService;

    @MockitoBean(name = "checkoutLockRegistry")
    private RedisLockRegistry checkoutLockRegistry;

    @MockitoBean
    private CheckoutTransactionService checkoutTransactionService;

    @Test
    void createOrder_releasesLockAfterTransactionCommits() throws Exception {
        // Arrange
        DistributedLock mockLock = mock(DistributedLock.class);
        when(checkoutLockRegistry.obtain(anyString())).thenReturn(mockLock);
        when(mockLock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);

        AtomicBoolean txActiveWhenExecuteCalled = new AtomicBoolean(false);
        when(checkoutTransactionService.execute(anyLong(), any())).thenAnswer(inv -> {
            txActiveWhenExecuteCalled.set(TransactionSynchronizationManager.isActualTransactionActive());
            return mock(OrderResponse.class);
        });

        // Act
        orderService.createOrder(1L, new OrderCreateRequest(10L, PaymentMethod.COD, null, null));

        // Assert: lock was unlocked exactly once
        verify(mockLock, times(1)).unlock();
        // Assert: outer createOrder() has NO active transaction (lock is outside TX)
        assertThat(txActiveWhenExecuteCalled.get()).isFalse();
    }
}
