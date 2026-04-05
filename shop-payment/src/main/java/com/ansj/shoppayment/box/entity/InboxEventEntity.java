package com.ansj.shoppayment.box.entity;

import com.ansj.shoppayment.common.UuidUtils;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "payment_inbox_event")
@Entity
public class InboxEventEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID eventId;

    @Column(name = "saga_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID sagaId;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Builder
    public InboxEventEntity(String eventType, UUID eventId, UUID sagaId,
                            UUID aggregateId, String aggregateType, String payload) {
        this.eventType = eventType;
        this.eventId = eventId;
        this.sagaId = sagaId;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.payload = payload;
        this.processedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UuidUtils.createV7();
        }
    }
}
