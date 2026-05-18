package com.ansj.shoporder.saga;

import com.ansj.shoporder.box.service.OutboxEventService;
import com.ansj.shoporder.common.AggregateId;
import com.ansj.shoporder.common.EventId;
import com.ansj.shoporder.common.SagaId;
import com.ansj.shoporder.metrics.SagaMetrics;
import com.ansj.shoporder.order.entity.OrderEntity;
import com.ansj.shoporder.order.entity.OrderItemEntity;
import com.ansj.shoporder.order.entity.OrderItemReservationStatus;
import com.ansj.shoporder.order.entity.OrderStatus;
import com.ansj.shoporder.order.event.inbound.PaymentFailedEvent;
import com.ansj.shoporder.order.event.inbound.PaymentSuccessEvent;
import com.ansj.shoporder.order.event.inbound.StockReserveFailedEvent;
import com.ansj.shoporder.order.event.inbound.StockReservedEvent;
import com.ansj.shoporder.order.event.outbound.PaymentRequestedEvent;
import com.ansj.shoporder.order.event.outbound.StockConfirmRequestedEvent;
import com.ansj.shoporder.order.event.outbound.StockReleaseRequestedEvent;
import com.ansj.shoporder.order.event.outbound.StockReservationRequestedEvent;
import com.ansj.shoporder.order.model.OrderItem;
import com.ansj.shoporder.order.model.Orders;
import com.ansj.shoporder.order.repository.OrderItemRepository;
import com.ansj.shoporder.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Order Saga Orchestrator.
 *
 * <p>Saga의 전체 흐름을 한 곳에서 관리한다.
 * "무엇을 언제 할지"는 이 클래스가 결정하고,
 * "어떻게 할지"는 각 서비스(stock, payment)가 담당한다.
 *
 * <pre>
 * start()               주문 생성 → 재고 예약 명령 fan-out
 *   ↓ stock-reserved
 * onStockReserved()     전체 예약 완료 시 → 결제 요청 명령
 *   ↓ payment-success
 * onPaymentSuccess()    결제 완료 → 재고 확정 명령 fan-out  [COMPLETED]
 *
 * onStockReserveFailed() 재고 부족 → 기예약 아이템 해제    [STOCK_FAILED]
 * onPaymentFailed()      결제 실패 → 재고 해제 명령 fan-out [CANCELLED]
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class OrderSagaOrchestrator {

    @Value("${shop.kafka.topics.stock-reservation-requested.topic}")
    private String stockReservationRequestedTopic;

    @Value("${shop.kafka.topics.payment-requested.topic}")
    private String paymentRequestedTopic;

    @Value("${shop.kafka.topics.stock-confirm-requested.topic}")
    private String stockConfirmRequestedTopic;

    @Value("${shop.kafka.topics.stock-release-requested.topic}")
    private String stockReleaseRequestedTopic;

    private final OrderService orderService;
    private final OrderItemRepository orderItemRepository;
    private final OutboxEventService outboxEventService;
    private final SagaMetrics sagaMetrics;

    // ─── Saga 시작 ────────────────────────────────────────────────────────────

    @Transactional
    public void start(Orders order, String traceId) {
        sagaMetrics.recordStarted();
        for (OrderItem item : order.getItems()) {
            outboxEventService.save(
                    buildReservationEvent(order, item, traceId),
                    stockReservationRequestedTopic,
                    item.getProductId().toString()
            );
        }
        log.info("[Orchestrator] Saga 시작. sagaId={}, items={}", order.getSagaId(), order.getItems().size());
    }

    // ─── 재고 예약 결과 수신 ──────────────────────────────────────────────────

    @Transactional
    public void onStockReserved(StockReservedEvent event) {
        OrderItemEntity item = findItemOrThrow(event.getOrderItemId());

        if (item.getReservationStatus() != OrderItemReservationStatus.PENDING) {
            log.info("[Orchestrator] 중복 stock-reserved 스킵. orderItemId={}", item.getOrderItemId());
            return;
        }
        item.markReserved();
        orderItemRepository.save(item);

        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());

        // 이미 실패 처리된 Saga에 뒤늦게 도착한 예약 → 즉시 해제
        if (order.getOrderStatus() == OrderStatus.STOCK_FAILED) {
            log.warn("[Orchestrator] 뒤늦은 stock-reserved → 즉시 해제. orderItemId={}", item.getOrderItemId());
            outboxEventService.save(
                    buildReleaseEvent(order, item, event.getTraceId(), "late-reserved-after-failure"),
                    stockReleaseRequestedTopic, item.getProductId().toString()
            );
            item.markReleased();
            orderItemRepository.save(item);
            return;
        }

        // 모든 아이템 예약 완료 → 결제 요청
        if (order.allItemsReserved() && order.getOrderStatus() == OrderStatus.PENDING) {
            order.stockReserved();
            sagaMetrics.recordTransition("PENDING", "STOCK_RESERVED");

            outboxEventService.save(
                    buildPaymentEvent(order, event.getTraceId()),
                    paymentRequestedTopic,
                    order.getSagaId().toString()
            );
            log.info("[Orchestrator] 전체 예약 완료 → 결제 요청. sagaId={}", event.getSagaId());
        }
    }

    @Transactional
    public void onStockReserveFailed(StockReserveFailedEvent event) {
        OrderItemEntity failedItem = findItemOrThrow(event.getOrderItemId());

        if (failedItem.getReservationStatus() == OrderItemReservationStatus.FAILED) {
            log.info("[Orchestrator] 중복 stock-reserve-failed 스킵. orderItemId={}", failedItem.getOrderItemId());
            return;
        }
        if (failedItem.getReservationStatus() == OrderItemReservationStatus.PENDING) {
            failedItem.markFailed();
            orderItemRepository.save(failedItem);
        }

        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());

        if (order.getOrderStatus() == OrderStatus.PENDING) {
            order.stockFailed();
            sagaMetrics.recordTransition("PENDING", "STOCK_FAILED");
            sagaMetrics.recordTerminated("STOCK_FAILED", order.getCreatedAt());
            log.info("[Orchestrator] 재고 부족 → Saga 실패. sagaId={}", event.getSagaId());
        }

        // 이미 예약된 다른 아이템 보상 해제
        for (OrderItemEntity it : order.getOrderItems()) {
            if (it.getReservationStatus() == OrderItemReservationStatus.RESERVED) {
                outboxEventService.save(
                        buildReleaseEvent(order, it, event.getTraceId(), "partial-reserve-failed"),
                        stockReleaseRequestedTopic, it.getProductId().toString()
                );
                it.markReleased();
                orderItemRepository.save(it);
            }
        }
    }

    // ─── 결제 결과 수신 ───────────────────────────────────────────────────────

    @Transactional
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());
        order.paymentCompleted();
        sagaMetrics.recordTransition("STOCK_RESERVED", "COMPLETED");
        sagaMetrics.recordTerminated("COMPLETED", order.getCreatedAt());

        // 재고 확정 명령 fan-out
        for (OrderItemEntity item : order.getOrderItems()) {
            if (item.getReservationStatus() == OrderItemReservationStatus.RESERVED) {
                outboxEventService.save(
                        buildConfirmEvent(order, item, event.getTraceId()),
                        stockConfirmRequestedTopic, item.getProductId().toString()
                );
                item.markConfirmed();
            }
        }
        log.info("[Orchestrator] 결제 완료 → 재고 확정 fan-out. sagaId={}", event.getSagaId());
    }

    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());
        order.paymentFailed();
        sagaMetrics.recordTransition("STOCK_RESERVED", "PAYMENT_FAILED");

        // 보상 트랜잭션: 재고 해제 fan-out
        for (OrderItemEntity item : order.getOrderItems()) {
            if (item.getReservationStatus() == OrderItemReservationStatus.RESERVED) {
                outboxEventService.save(
                        buildReleaseEvent(order, item, event.getTraceId(), "payment-failed"),
                        stockReleaseRequestedTopic, item.getProductId().toString()
                );
                item.markReleased();
            }
        }

        order.compensationCompleted();
        sagaMetrics.recordTransition("PAYMENT_FAILED", "CANCELLED");
        sagaMetrics.recordTerminated("CANCELLED", order.getCreatedAt());
        log.info("[Orchestrator] 결제 실패 → 보상 완료. sagaId={}", event.getSagaId());
    }

    // ─── 이벤트 빌더 ──────────────────────────────────────────────────────────

    private StockReservationRequestedEvent buildReservationEvent(Orders order, OrderItem item, String traceId) {
        StockReservationRequestedEvent event = StockReservationRequestedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(SagaId.from(order.getSagaId()))
                .aggregateId(AggregateId.from(order.getOrderId()))
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .orderItemId(item.getOrderItemId())
                .build();
        event.setTraceId(traceId);
        return event;
    }

    private PaymentRequestedEvent buildPaymentEvent(OrderEntity order, String traceId) {
        PaymentRequestedEvent event = PaymentRequestedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(SagaId.from(order.getSagaId()))
                .aggregateId(AggregateId.from(order.getOrderId()))
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .build();
        event.setTraceId(traceId);
        return event;
    }

    private StockConfirmRequestedEvent buildConfirmEvent(OrderEntity order, OrderItemEntity item, String traceId) {
        StockConfirmRequestedEvent event = StockConfirmRequestedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(SagaId.from(order.getSagaId()))
                .aggregateId(AggregateId.from(order.getOrderId()))
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .orderItemId(item.getOrderItemId())
                .build();
        event.setTraceId(traceId);
        return event;
    }

    private StockReleaseRequestedEvent buildReleaseEvent(OrderEntity order, OrderItemEntity item,
                                                          String traceId, String reason) {
        StockReleaseRequestedEvent event = StockReleaseRequestedEvent.builder()
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
        event.setTraceId(traceId);
        return event;
    }

    private OrderItemEntity findItemOrThrow(UUID orderItemId) {
        return orderItemRepository.findByOrderItemId(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "orderItem 이 존재하지 않습니다. orderItemId=" + orderItemId));
    }
}
