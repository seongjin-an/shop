package com.ansj.shopstock.usecase;

import com.ansj.shopstock.event.service.InboxEventService;
import com.ansj.shopstock.event.service.OutboxEventService;
import com.ansj.shopstock.stock.dto.inbound.ProductCreatedEvent;
import com.ansj.shopstock.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class StockUseCase {

    private final StockService stockService;
    private final InboxEventService inboxEventService;
    private final OutboxEventService outboxEventService;

    
    public void processIncreaseStockEvent(ProductCreatedEvent productCreatedEvent) {

        if (inboxEventService.existsByEventId(productCreatedEvent.getEventId())) {
            return;
        }

        stockService.create(productCreatedEvent.getItems());

        inboxEventService.createInboxEvent(productCreatedEvent);

        //outboxEventService.stockReservedEvent(productCreatedEvent.getSagaId(), productCreatedEvent.getAggregateId());
    }
}
