package com.ansj.shoporder.order.repository;

import com.ansj.shoporder.order.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findBySagaId(UUID sagaId);
}
