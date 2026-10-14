package com.aitechmarketplace.backend.order.repository;

import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<Order> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"items"})
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<Order> findByIdAndUserId(Long id, Long userId);
}
