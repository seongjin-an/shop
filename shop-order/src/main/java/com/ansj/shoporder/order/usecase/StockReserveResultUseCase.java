package com.ansj.shoporder.order.usecase;

import com.ansj.shoporder.common.*;
import com.ansj.shoporder.order.entity.OrderEntity;
import com.ansj.shoporder.order.event.inbound.StockReserveFailedEvent;
import com.ansj.shoporder.order.event.inbound.StockReservedEvent;
import com.ansj.shoporder.order.event.outbound.PaymentRequestedEvent;
import com.ansj.shoporder.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class StockReserveResultUseCase {

    @Value("${shop.kafka.topics.payment-requested.topic}")
    private String paymentRequestedTopic;

    private final OrderService orderService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonUtil jsonUtil;

    /**
     * stock-reserved 수신 → PENDING → STOCK_RESERVED 전이 → payment-requested 발행
     */
    public void onStockReserved(StockReservedEvent event) {
        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());
        order.stockReserved();

        PaymentRequestedEvent paymentEvent = PaymentRequestedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(event.getSagaId())
                .aggregateId(AggregateId.from(order.getOrderId()))
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .build();

        jsonUtil.toJson(paymentEvent)
                .ifPresentOrElse(
                        json -> kafkaTemplate.send(paymentRequestedTopic, event.getSagaId().toString(), json),
                        () -> log.error("payment-requested 직렬화 실패. sagaId={}", event.getSagaId())
                );
    }

    /**
     * stock-reserve-failed 수신 → PENDING → STOCK_FAILED 전이 (terminal)
     * 재고 부족으로 주문이 종료되는 케이스. 추가 이벤트 발행 없음.
     */
    public void onStockReserveFailed(StockReserveFailedEvent event) {
        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());
        order.stockFailed();
        log.info("재고 부족으로 주문 실패 처리. sagaId={}, reason={}", event.getSagaId(), event.getReason());
    }
}
