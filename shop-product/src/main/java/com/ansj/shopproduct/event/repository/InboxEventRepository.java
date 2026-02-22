package com.ansj.shopproduct.event.repository;

import com.ansj.shopproduct.event.entity.InboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEventEntity, Long> {
    boolean existsByEventId(UUID eventId);
}
