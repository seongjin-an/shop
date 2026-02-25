package com.ansj.shopstock.event.service;

import com.ansj.shopstock.common.*;
import com.ansj.shopstock.event.entity.OutboxEventEntity;
import com.ansj.shopstock.event.repository.OutboxEventRepository;
import com.ansj.shopstock.stock.dto.outbound.StockReservedEvent;
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
    public void stockReservedEvent(SagaId sagaId, AggregateId aggregateId) {
        EventId eventId = EventId.newId();
        String aggregateType = "STOCK";
        LocalDateTime now = LocalDateTime.now();
        StockReservedEvent stockReservedEvent = StockReservedEvent.builder()
                .eventId(eventId)
                .sagaId(sagaId)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .occurredAt(now)
                .build();

        String payload = jsonUtil.toJson(stockReservedEvent).orElseThrow(RuntimeException::new);
        OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .eventId(eventId.id())
                .sagaId(sagaId.id())
                .eventType(MessageType.STOCK_RESERVED)
                .aggregateId(aggregateId.id())
                .aggregateType(aggregateType)
                .payload(payload)
                .build();
        outboxEventRepository.save(outboxEventEntity);
    }
}
