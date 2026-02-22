package com.ansj.shopproduct.event.service;

import com.ansj.shopproduct.common.BaseEvent;
import com.ansj.shopproduct.common.EventId;
import com.ansj.shopproduct.common.JsonUtil;
import com.ansj.shopproduct.event.entity.InboxEventEntity;
import com.ansj.shopproduct.event.repository.InboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class InboxEventService {

    private final InboxEventRepository inboxEventRepository;
    private final JsonUtil jsonUtil;

    public boolean existsByEventId(EventId eventId) {
        return inboxEventRepository.existsByEventId(eventId.id());
    }

    @Transactional
    public void createInboxEvent(BaseEvent event) {
        String json = jsonUtil.toJson(event).orElseThrow();
        InboxEventEntity inboxEvent = InboxEventEntity.builder()
                .eventType(event.getEventType())
                .eventId(event.getEventId().id())
                .sagaId(event.getSagaId().id())
                .aggregateId(event.getAggregateId().id())
                .aggregateType(event.getAggregateType())
                .payload(json)
                .build();
        inboxEventRepository.save(inboxEvent);
    }
}
