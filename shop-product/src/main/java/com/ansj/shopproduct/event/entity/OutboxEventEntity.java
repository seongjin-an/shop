package com.ansj.shopproduct.event.entity;


import com.ansj.shopproduct.common.UuidUtils;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "product_outbox_event",
        indexes = {
                @Index(name = "idx_product_outbox_event_saga_id", columnList = "saga_id")
        }
)
public class OutboxEventEntity {
    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    // 이 이벤트 자체의 고유 ID (절대 재사용 X)
    @Column(name = "event_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID eventId;

    // Saga 흐름 식별자 (처음 이벤트에서 생성, 이후 유지)
    @Column(name = "saga_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID sagaId;

    @Column(name = "event_type", nullable = false)
    private String eventType;        // ex) OrderCreated

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;    // ex) ORDER

    @Column(name = "aggregate_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID aggregateId;      // ex) orderId

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;          // JSON

    //@Enumerated(EnumType.STRING) // 추후에 수정하자
    @Column(name = "status", nullable = false)
    private String status;     // NEW, SENT

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "retry_count")
    private Integer retryCount;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidUtils.createV7();
        }
    }

    @Builder
    public OutboxEventEntity(
            UUID eventId,
            UUID sagaId,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            String payload
    ) {
        this.eventId = eventId;
        this.sagaId = sagaId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = "NEW";
        this.createdAt = LocalDateTime.now();
    }

    public void markAsSent() {
        this.status = "SENT";
        this.sentAt = LocalDateTime.now();
    }
}
