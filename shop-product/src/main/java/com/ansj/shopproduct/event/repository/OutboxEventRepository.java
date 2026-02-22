package com.ansj.shopproduct.event.repository;

import com.ansj.shopproduct.event.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
}
