package com.ansj.shoporder.usecase;

import com.ansj.shoporder.common.*;
import com.ansj.shoporder.order.entity.OrderEntity;
import com.ansj.shoporder.order.event.inbound.PaymentFailedEvent;
import com.ansj.shoporder.order.event.inbound.PaymentSuccessEvent;
import com.ansj.shoporder.order.event.outbound.OrderCancelledEvent;
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
public class PaymentResultUseCase {

    @Value("${shop.kafka.topics.order-cancelled.topic}")
    private String orderCancelledTopic;

    private final OrderService orderService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonUtil jsonUtil;

    /**
     * payment-success 수신 → STOCK_RESERVED → COMPLETED (terminal)
     */
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());
        order.paymentCompleted();
        log.info("결제 완료. sagaId={}", event.getSagaId());
    }

    /**
     * payment-failed 수신 → STOCK_RESERVED → PAYMENT_FAILED → order-cancelled 발행 → CANCELLED
     * 보상 트랜잭션: shop-stock 이 order-cancelled 수신하여 예약 재고 복구
     */
    public void onPaymentFailed(PaymentFailedEvent event) {
        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());
        order.paymentFailed();

        OrderCancelledEvent cancelledEvent = OrderCancelledEvent.builder()
                .eventId(EventId.newId())
                .sagaId(event.getSagaId())
                .aggregateId(AggregateId.from(order.getOrderId()))
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .build();

        jsonUtil.toJson(cancelledEvent)
                .ifPresentOrElse(
                        json -> kafkaTemplate.send(orderCancelledTopic, event.getSagaId().toString(), json),
                        () -> log.error("order-cancelled 직렬화 실패. sagaId={}", event.getSagaId())
                );

        // 보상 이벤트 발행 후 즉시 CANCELLED 로 전이 (stock 보상 완료 응답을 기다리지 않음)
        order.compensationCompleted();
        log.info("결제 실패 — 보상 트랜잭션 시작. sagaId={}, reason={}", event.getSagaId(), event.getReason());
    }
}
