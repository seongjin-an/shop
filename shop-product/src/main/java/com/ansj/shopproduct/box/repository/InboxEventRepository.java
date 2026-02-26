package com.ansj.shopproduct.box.repository;

import com.ansj.shopproduct.box.entity.InboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEventEntity, UUID> {
    boolean existsByEventId(UUID eventId);
}
