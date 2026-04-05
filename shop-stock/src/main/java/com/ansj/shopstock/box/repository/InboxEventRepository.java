package com.ansj.shopstock.box.repository;

import com.ansj.shopstock.box.entity.InboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEventEntity, UUID> {
    boolean existsByEventId(UUID eventId);
    Optional<InboxEventEntity> findBySagaIdAndEventType(UUID sagaId, String eventType);
}
