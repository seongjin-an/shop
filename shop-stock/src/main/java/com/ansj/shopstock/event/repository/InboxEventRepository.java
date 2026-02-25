package com.ansj.shopstock.event.repository;

import com.ansj.shopstock.event.entity.InboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEventEntity, UUID> {
    boolean existsByEventId(UUID eventId);
}
