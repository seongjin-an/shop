package com.ansj.shoppayment.box.service;

import com.ansj.shoppayment.box.entity.OutboxEventEntity;
import com.ansj.shoppayment.box.repository.OutboxEventRepository;
import com.ansj.shoppayment.common.BaseEvent;
import com.ansj.shoppayment.common.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonUtil jsonUtil;

    @Transactional
    public void save(BaseEvent event, String destinationTopic, String partitionKey) {
        String payload = jsonUtil.toJson(event)
                .orElseThrow(() -> new IllegalStateException("outbox 직렬화 실패. eventId=" + event.getEventId()));
        OutboxEventEntity entity = OutboxEventEntity.builder()
                .eventId(event.getEventId().id())
                .sagaId(event.getSagaId().id())
                .eventType(event.getEventType())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId().id())
                .payload(payload)
                .destinationTopic(destinationTopic)
                .partitionKey(partitionKey)
                .build();
        outboxEventRepository.save(entity);
    }
}
