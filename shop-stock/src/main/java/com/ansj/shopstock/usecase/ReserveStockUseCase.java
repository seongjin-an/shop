package com.ansj.shopstock.usecase;

import com.ansj.shopstock.box.service.InboxEventService;
import com.ansj.shopstock.stock.event.inbound.StockReservationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ReserveStockUseCase {

    private final InboxEventService inboxEventService;
    private final ReserveStockTransactionService transactionService;

    public void processReservationRequested(StockReservationRequestedEvent event) {
        if (inboxEventService.existsByEventId(event.getEventId())) {
            log.info("중복 이벤트 무시. eventId={}", event.getEventId());
            return;
        }

        try {
            // 재고 예약 + inbox + outbox(reserved) 를 하나의 트랜잭션으로 커밋
            transactionService.reserveAndSaveOutbox(event);
        } catch (Exception e) {
            log.warn("재고 예약 실패. sagaId={}, productId={}, cause={}",
                    event.getSagaId(), event.getProductId(), e.getMessage());
            // inbox + outbox(failed) 를 별도 트랜잭션으로 커밋
            transactionService.failAndSaveOutbox(event, e.getMessage());
        }
    }
}
