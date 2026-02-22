package com.ansj.shopproduct.event.service;

import com.ansj.shopproduct.common.*;
import com.ansj.shopproduct.event.entity.OutboxEventEntity;
import com.ansj.shopproduct.event.repository.OutboxEventRepository;
import com.ansj.shopproduct.inventory.dto.outbound.InventoryReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonUtil jsonUtil;

    @Transactional
    public void inventoryReservedEvent(SagaId sagaId, AggregateId aggregateId) {
        EventId eventId = EventId.newId();
        String aggregateType = "INVENTORY";
        LocalDateTime now = LocalDateTime.now();
        InventoryReservedEvent inventoryReservedEvent = InventoryReservedEvent.builder()
                .eventId(eventId)
                .sagaId(sagaId)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .occurredAt(now)
                .build();

        String payload = jsonUtil.toJson(inventoryReservedEvent).orElseThrow(RuntimeException::new);
        OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .eventId(eventId.id())
                .sagaId(sagaId.id())
                .eventType(MessageType.INVENTORY_RESERVED)
                .aggregateId(aggregateId.id())
                .aggregateType(aggregateType)
                .payload(payload)
                .build();
        outboxEventRepository.save(outboxEventEntity);
    }
}
