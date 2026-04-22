package com.ansj.shopstock.usecase;

import com.ansj.shopstock.box.service.InboxEventService;
import com.ansj.shopstock.stock.event.inbound.StockConfirmRequestedEvent;
import com.ansj.shopstock.stock.event.inbound.StockReleaseRequestedEvent;
import com.ansj.shopstock.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 재고 확정/해제 보상 유스케이스.
 *
 * <p>변경 이전: order-level {@code payment-success}, {@code order-cancelled} 수신 →
 *  inbox 에서 원본 주문 페이로드 조회 → 모든 아이템을 한 트랜잭션에서 처리.
 *
 * <p>변경 이후: per-item {@code stock-confirm-requested}, {@code stock-release-requested}
 *  수신 → 단일 StockEntity 만 update. partition key=productId 이므로 동일 상품은
 *  반드시 동일 스레드 직렬 처리 → 동시 write 소멸.
 *
 * <p>레거시 order-level 리스너는 호환성 유지용으로 잠시 남겨두지만 새 흐름에서는 동작하지 않는다
 *  (shop-order 가 더 이상 해당 토픽으로 발행하지 않음).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CompensateStockUseCase {

    private final StockService stockService;
    private final InboxEventService inboxEventService;

    // ─── per-item 신규 흐름 ──────────────────────────────────────────────────

    public void onStockConfirmRequested(StockConfirmRequestedEvent event) {
        if (inboxEventService.existsByEventId(event.getEventId())) {
            log.info("중복 이벤트 무시. eventId={}", event.getEventId());
            return;
        }
        stockService.confirmReservationOne(event.getProductId(), event.getQuantity());
        inboxEventService.createInboxEvent(event);
        log.info("재고 예약 확정 완료. sagaId={}, productId={}, qty={}",
                event.getSagaId(), event.getProductId(), event.getQuantity());
    }

    public void onStockReleaseRequested(StockReleaseRequestedEvent event) {
        if (inboxEventService.existsByEventId(event.getEventId())) {
            log.info("중복 이벤트 무시. eventId={}", event.getEventId());
            return;
        }
        stockService.cancelReservationOne(event.getProductId(), event.getQuantity());
        inboxEventService.createInboxEvent(event);
        log.info("재고 보상 완료. sagaId={}, productId={}, qty={}, reason={}",
                event.getSagaId(), event.getProductId(), event.getQuantity(), event.getReason());
    }

    // 레거시 order-level 보상(onPaymentSuccess / onOrderCancelled)은 제거됨.
    // per-item stock-confirm-requested / stock-release-requested 흐름으로 완전 이관.
}
