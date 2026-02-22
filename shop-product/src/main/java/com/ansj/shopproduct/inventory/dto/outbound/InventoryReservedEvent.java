package com.ansj.shopproduct.inventory.dto.outbound;

import com.ansj.shopproduct.common.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class InventoryReservedEvent extends BaseEvent {

    @Builder
    public InventoryReservedEvent(EventId eventId,
                                  SagaId sagaId,
                                  AggregateId aggregateId,
                                  String aggregateType,
                                  LocalDateTime occurredAt) {
        super(MessageType.INVENTORY_RESERVED, eventId, sagaId, aggregateId, aggregateType, occurredAt);
    }
}
