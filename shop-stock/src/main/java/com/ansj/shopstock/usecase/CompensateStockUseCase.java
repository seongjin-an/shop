package com.ansj.shopstock.usecase;

import com.ansj.shopstock.box.repository.InboxEventRepository;
import com.ansj.shopstock.box.service.InboxEventService;
import com.ansj.shopstock.common.*;
import com.ansj.shopstock.stock.dto.StockItem;
import com.ansj.shopstock.stock.event.inbound.OrderCancelledEvent;
import com.ansj.shopstock.stock.event.inbound.OrderCreatedEvent;
import com.ansj.shopstock.stock.event.inbound.PaymentSuccessEvent;
import com.ansj.shopstock.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class CompensateStockUseCase {

    private final StockService stockService;
    private final InboxEventService inboxEventService;
    private final InboxEventRepository inboxEventRepository;
    private final JsonUtil jsonUtil;

    /**
     * payment-success 수신 → reservedQuantity 차감 (예약 확정)
     *
     * <p>예외를 catch하지 않고 호출자(StockKafkaConsumer)까지 전파한다.
     * 리스너가 ack를 생략하면 Kafka가 메시지를 재전달하므로 유실 없이 재처리된다.
     */
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        if (inboxEventService.existsByEventId(event.getEventId())) {
            log.info("중복 이벤트 무시. eventId={}", event.getEventId());
            return;
        }

        List<StockItem> items = getItemsBySagaId(event.getSagaId().id());
        stockService.confirmReservations(items);
        inboxEventService.createInboxEvent(event);
        log.info("재고 예약 확정 완료. sagaId={}", event.getSagaId());
    }

    /**
     * order-cancelled 수신 → reservedQuantity 복구 (보상 트랜잭션)
     *
     * <p>예외를 catch하지 않고 호출자(StockKafkaConsumer)까지 전파한다.
     * 리스너가 ack를 생략하면 Kafka가 메시지를 재전달하므로 유실 없이 재처리된다.
     */
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (inboxEventService.existsByEventId(event.getEventId())) {
            log.info("중복 이벤트 무시. eventId={}", event.getEventId());
            return;
        }

        List<StockItem> items = getItemsBySagaId(event.getSagaId().id());
        stockService.cancelReservations(items);
        inboxEventService.createInboxEvent(event);
        log.info("재고 보상 완료. sagaId={}", event.getSagaId());
    }

    /**
     * 인박스에 저장된 order-created 이벤트 페이로드에서 아이템 목록을 복원한다.
     */
    private List<StockItem> getItemsBySagaId(UUID sagaId) {
        String payload = inboxEventRepository
                .findBySagaIdAndEventType(sagaId, MessageType.ORDER_CREATED)
                .orElseThrow(() -> new IllegalStateException("order-created 인박스 미존재. sagaId=" + sagaId))
                .getPayload();

        OrderCreatedEvent orderEvent = jsonUtil.fromJson(payload, OrderCreatedEvent.class)
                .orElseThrow(() -> new IllegalStateException("order-created 페이로드 역직렬화 실패. sagaId=" + sagaId));

        return orderEvent.getItems().stream()
                .map(item -> new StockItem(AggregateId.from(item.getProductId()), item.getQuantity()))
                .toList();
    }
}
