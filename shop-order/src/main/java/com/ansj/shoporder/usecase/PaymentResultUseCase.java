package com.ansj.shoporder.usecase;

import com.ansj.shoporder.common.AggregateId;
import com.ansj.shoporder.common.EventId;
import com.ansj.shoporder.common.JsonUtil;
import com.ansj.shoporder.common.SagaAwareKafkaPublisher;
import com.ansj.shoporder.common.SagaId;
import com.ansj.shoporder.metrics.SagaMetrics;
import com.ansj.shoporder.order.entity.OrderEntity;
import com.ansj.shoporder.order.entity.OrderItemEntity;
import com.ansj.shoporder.order.entity.OrderItemReservationStatus;
import com.ansj.shoporder.order.event.inbound.PaymentFailedEvent;
import com.ansj.shoporder.order.event.inbound.PaymentSuccessEvent;
import com.ansj.shoporder.order.event.outbound.StockConfirmRequestedEvent;
import com.ansj.shoporder.order.event.outbound.StockReleaseRequestedEvent;
import com.ansj.shoporder.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class PaymentResultUseCase {

    @Value("${shop.kafka.topics.stock-confirm-requested.topic}")
    private String stockConfirmRequestedTopic;

    @Value("${shop.kafka.topics.stock-release-requested.topic}")
    private String stockReleaseRequestedTopic;

    private final OrderService orderService;
    private final SagaAwareKafkaPublisher kafkaPublisher;
    private final JsonUtil jsonUtil;
    private final SagaMetrics sagaMetrics;

    public void onPaymentSuccess(PaymentSuccessEvent event) {
        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());
        order.paymentCompleted();
        sagaMetrics.recordTransition("STOCK_RESERVED", "COMPLETED");
        sagaMetrics.recordTerminated("COMPLETED", order.getCreatedAt());

        String sagaIdStr = order.getSagaId().toString();
        String traceId = event.getTraceId();
        for (OrderItemEntity item : order.getOrderItems()) {
            if (item.getReservationStatus() == OrderItemReservationStatus.RESERVED) {
                publishConfirm(order, item, sagaIdStr, traceId);
                item.markConfirmed();
            }
        }
        log.info("결제 완료 & 재고 확정 fan-out 완료. sagaId={}, items={}",
                event.getSagaId(), order.getOrderItems().size());
    }

    public void onPaymentFailed(PaymentFailedEvent event) {
        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());
        order.paymentFailed();
        sagaMetrics.recordTransition("STOCK_RESERVED", "PAYMENT_FAILED");

        String sagaIdStr = order.getSagaId().toString();
        String traceId = event.getTraceId();
        for (OrderItemEntity item : order.getOrderItems()) {
            if (item.getReservationStatus() == OrderItemReservationStatus.RESERVED) {
                publishRelease(order, item, sagaIdStr, traceId, "payment-failed");
                item.markReleased();
            }
        }

        order.compensationCompleted();
        sagaMetrics.recordTransition("PAYMENT_FAILED", "CANCELLED");
        sagaMetrics.recordTerminated("CANCELLED", order.getCreatedAt());
        log.info("결제 실패 — 재고 해제 fan-out 완료. sagaId={}, reason={}",
                event.getSagaId(), event.getReason());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void publishConfirm(OrderEntity order, OrderItemEntity item, String sagaIdStr, String traceId) {
        StockConfirmRequestedEvent confirmEvent = StockConfirmRequestedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(SagaId.from(order.getSagaId()))
                .aggregateId(AggregateId.from(order.getOrderId()))
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .orderItemId(item.getOrderItemId())
                .build();
        confirmEvent.setTraceId(traceId);

        jsonUtil.toJson(confirmEvent).ifPresentOrElse(
                json -> kafkaPublisher.send(
                        stockConfirmRequestedTopic,
                        item.getProductId().toString(),
                        sagaIdStr,
                        traceId,
                        json
                ),
                () -> log.error("stock-confirm-requested 직렬화 실패. orderItemId={}", item.getOrderItemId())
        );
    }

    private void publishRelease(OrderEntity order, OrderItemEntity item, String sagaIdStr, String traceId, String reason) {
        StockReleaseRequestedEvent releaseEvent = StockReleaseRequestedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(SagaId.from(order.getSagaId()))
                .aggregateId(AggregateId.from(order.getOrderId()))
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .orderItemId(item.getOrderItemId())
                .reason(reason)
                .build();
        releaseEvent.setTraceId(traceId);

        jsonUtil.toJson(releaseEvent).ifPresentOrElse(
                json -> kafkaPublisher.send(
                        stockReleaseRequestedTopic,
                        item.getProductId().toString(),
                        sagaIdStr,
                        traceId,
                        json
                ),
                () -> log.error("stock-release-requested 직렬화 실패. orderItemId={}", item.getOrderItemId())
        );
    }
}
