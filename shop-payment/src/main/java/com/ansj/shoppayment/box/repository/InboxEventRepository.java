package com.ansj.shoppayment.box.repository;

import com.ansj.shoppayment.box.entity.InboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEventEntity, UUID> {
    boolean existsByEventId(UUID eventId);
}
