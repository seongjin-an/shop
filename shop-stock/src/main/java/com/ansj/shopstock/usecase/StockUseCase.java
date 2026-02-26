package com.ansj.shopstock.usecase;

import com.ansj.shopstock.box.service.InboxEventService;
import com.ansj.shopstock.stock.dto.StockItem;
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

    
    public void processIncreaseStockEvent(ProductCreatedEvent productCreatedEvent) {

        if (inboxEventService.existsByEventId(productCreatedEvent.getEventId())) {
            return;
        }

        StockItem item = productCreatedEvent.getItem();
        stockService.createStock(item.getProductId().id(), item.getQuantity());

        inboxEventService.createInboxEvent(productCreatedEvent);
    }
}
