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
import com.ansj.shoporder.order.entity.OrderStatus;
import com.ansj.shoporder.order.event.inbound.StockReserveFailedEvent;
import com.ansj.shoporder.order.event.inbound.StockReservedEvent;
import com.ansj.shoporder.order.event.outbound.PaymentRequestedEvent;
import com.ansj.shoporder.order.event.outbound.StockReleaseRequestedEvent;
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
 * per-item 예약 결과 집계(Aggregator).
 *
 * <pre>
 *  [수신]  stock-reserved / stock-reserve-failed  (per-item)
 *          ↓
 *  [집계]  OrderItemEntity.reservationStatus = RESERVED / FAILED
 *          ↓
 *  [판정]  같은 sagaId 의 모든 아이템이 RESERVED 인가?
 *          - YES → payment-requested 발행 (STOCK_RESERVED 로 전이)
 *          - 하나라도 FAILED → 이미 RESERVED 된 아이템에 대해
 *                             stock-release-requested fan-out + STOCK_FAILED 전이
 *          - 미완료 → 아무것도 안 함 (다음 per-item 이벤트 대기)
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class StockReserveResultUseCase {

    @Value("${shop.kafka.topics.payment-requested.topic}")
    private String paymentRequestedTopic;

    @Value("${shop.kafka.topics.stock-release-requested.topic}")
    private String stockReleaseRequestedTopic;

    private final OrderService orderService;
    private final OrderItemRepository orderItemRepository;
    private final SagaAwareKafkaPublisher kafkaPublisher;
    private final JsonUtil jsonUtil;
    private final SagaMetrics sagaMetrics;

    /**
     * stock-reserved (per-item) 수신.
     * <ol>
     *   <li>해당 OrderItemEntity 를 RESERVED 로 전이</li>
     *   <li>같은 order 의 모든 아이템이 RESERVED 인지 확인</li>
     *   <li>전부 RESERVED → PENDING → STOCK_RESERVED, payment-requested 발행</li>
     * </ol>
     */
    public void onStockReserved(StockReservedEvent event) {
        OrderItemEntity item = findItemOrThrow(event.getOrderItemId());

        // 재실행/중복 수신 시(같은 orderItemId 가 이미 RESERVED/CONFIRMED/RELEASED 일 수 있음) idempotent 처리
        if (item.getReservationStatus() != OrderItemReservationStatus.PENDING) {
            log.info("중복 stock-reserved 스킵. orderItemId={}, status={}",
                    item.getOrderItemId(), item.getReservationStatus());
            return;
        }
        item.markReserved();
        orderItemRepository.save(item);

        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());

        // 이미 실패 fan-out 이 진행 중(STOCK_FAILED) 인 경우에는 보상 대상으로 흘려보내야 함.
        // → 즉시 stock-release-requested 를 역으로 발행.
        if (order.getOrderStatus() == OrderStatus.STOCK_FAILED) {
            log.warn("뒤늦게 도착한 stock-reserved 를 보상 대상으로 처리. orderItemId={}, sagaId={}",
                    item.getOrderItemId(), event.getSagaId());
            publishRelease(order, item, "late-reserved-after-failure");
            item.markReleased();
            orderItemRepository.save(item);
            return;
        }

        if (order.allItemsReserved() && order.getOrderStatus() == OrderStatus.PENDING) {
            order.stockReserved();
            sagaMetrics.recordTransition("PENDING", "STOCK_RESERVED");
            publishPaymentRequested(order);
        }
    }

    /**
     * stock-reserve-failed (per-item) 수신.
     * <ol>
     *   <li>해당 아이템을 FAILED 로 전이</li>
     *   <li>이미 RESERVED 된 다른 아이템들에 대해 stock-release-requested fan-out (부분 롤백)</li>
     *   <li>주문을 STOCK_FAILED 로 전이 (멱등: 이미 STOCK_FAILED 면 스킵)</li>
     * </ol>
     * 아직 PENDING 상태인 다른 아이템이 있더라도, 그들이 나중에 RESERVED 로 올라오면
     * {@link #onStockReserved} 의 late-reserved 분기가 처리한다.
     */
    public void onStockReserveFailed(StockReserveFailedEvent event) {
        OrderItemEntity failedItem = findItemOrThrow(event.getOrderItemId());

        if (failedItem.getReservationStatus() == OrderItemReservationStatus.FAILED) {
            log.info("중복 stock-reserve-failed 스킵. orderItemId={}", failedItem.getOrderItemId());
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
            log.info("재고 부족으로 주문 실패 처리. sagaId={}, reason={}",
                    event.getSagaId(), event.getReason());
        }

        // 이미 예약된 다른 아이템에 대해 보상 fan-out
        for (OrderItemEntity it : order.getOrderItems()) {
            if (it.getReservationStatus() == OrderItemReservationStatus.RESERVED) {
                publishRelease(order, it, "partial-reserve-failed");
                it.markReleased();
                orderItemRepository.save(it);
            }
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private OrderItemEntity findItemOrThrow(UUID orderItemId) {
        return orderItemRepository.findByOrderItemId(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "orderItem 이 존재하지 않습니다. orderItemId=" + orderItemId));
    }

    private void publishPaymentRequested(OrderEntity order) {
        PaymentRequestedEvent paymentEvent = PaymentRequestedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(SagaId.from(order.getSagaId()))
                .aggregateId(AggregateId.from(order.getOrderId()))
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .build();

        jsonUtil.toJson(paymentEvent).ifPresentOrElse(
                json -> kafkaPublisher.send(
                        paymentRequestedTopic,
                        order.getSagaId().toString(),  // payment 는 order 단위 → sagaId 유지
                        order.getSagaId().toString(),
                        json
                ),
                () -> log.error("payment-requested 직렬화 실패. sagaId={}", order.getSagaId())
        );
    }

    private void publishRelease(OrderEntity order, OrderItemEntity item, String reason) {
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

        jsonUtil.toJson(releaseEvent).ifPresentOrElse(
                json -> kafkaPublisher.send(
                        stockReleaseRequestedTopic,
                        item.getProductId().toString(),  // partition key = productId
                        order.getSagaId().toString(),
                        json
                ),
                () -> log.error("stock-release-requested 직렬화 실패. orderItemId={}", item.getOrderItemId())
        );
    }
}
