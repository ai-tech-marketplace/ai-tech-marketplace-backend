package com.aitechmarketplace.backend.event;

import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.order.entity.PaymentStatus;
import com.aitechmarketplace.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
class OrderEventBridgeTest {

    @Autowired
    private ApplicationEventPublisher appEventPublisher;

    @MockitoBean
    private DomainEventPublisher domainEventPublisher;

    @Test
    void onOrderCreated_delegatesToDomainEventPublisher() {
        Order order = createTestOrder("ORD-EVENT-001");
        appEventPublisher.publishEvent(new OrderEventBridge.OrderCreatedApplicationEvent(order));

        verify(domainEventPublisher).publishOrderCreated(argThat(event ->
                event.orderNumber().equals("ORD-EVENT-001") &&
                event.userId().equals(1L)
        ));
    }

    @Test
    void onOrderCancelled_delegatesToDomainEventPublisher() {
        Order order = createTestOrder("ORD-EVENT-002");
        appEventPublisher.publishEvent(new OrderEventBridge.OrderCancelledApplicationEvent(order, "Customer requested cancel"));

        verify(domainEventPublisher).publishOrderCancelled(argThat(event ->
                event.orderNumber().equals("ORD-EVENT-002") &&
                event.reason().equals("Customer requested cancel")
        ));
    }

    private Order createTestOrder(String orderNumber) {
        User user = new User();
        user.setId(1L);

        Order order = new Order();
        order.setId(100L);
        order.setOrderNumber(orderNumber);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setSubtotal(new BigDecimal("100000.00"));
        order.setShippingFee(new BigDecimal("30000.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("130000.00"));
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        order.setItems(new ArrayList<>());
        return order;
    }
}
