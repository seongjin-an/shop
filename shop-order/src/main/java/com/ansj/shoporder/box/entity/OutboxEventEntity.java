package com.ansj.shoporder.box.entity;

import com.ansj.shoporder.common.UuidUtils;
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
        name = "order_outbox_event",
        indexes = {
                @Index(name = "idx_order_outbox_event_saga_id", columnList = "saga_id")
        }
)
public class OutboxEventEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID eventId;

    @Column(name = "saga_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID sagaId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID aggregateId;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    // Debezium EventRouter: 이 값이 Kafka 토픽 이름으로 사용됨
    @Column(name = "destination_topic", nullable = false)
    private String destinationTopic;

    // Debezium EventRouter: 이 값이 Kafka 메시지 키(파티션 키)로 사용됨
    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidUtils.createV7();
        }
    }

    @Builder
    public OutboxEventEntity(UUID eventId, UUID sagaId, String eventType,
                             String aggregateType, UUID aggregateId, String payload,
                             String destinationTopic, String partitionKey) {
        this.eventId = eventId;
        this.sagaId = sagaId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.destinationTopic = destinationTopic;
        this.partitionKey = partitionKey;
        this.createdAt = LocalDateTime.now();
    }
}
