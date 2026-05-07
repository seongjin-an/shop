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

    public void onStockReserved(StockReservedEvent event) {
        OrderItemEntity item = findItemOrThrow(event.getOrderItemId());

        if (item.getReservationStatus() != OrderItemReservationStatus.PENDING) {
            log.info("중복 stock-reserved 스킵. orderItemId={}, status={}",
                    item.getOrderItemId(), item.getReservationStatus());
            return;
        }
        item.markReserved();
        orderItemRepository.save(item);

        OrderEntity order = orderService.getOrderBySagaId(event.getSagaId().id());

        if (order.getOrderStatus() == OrderStatus.STOCK_FAILED) {
            log.warn("뒤늦게 도착한 stock-reserved 를 보상 대상으로 처리. orderItemId={}, sagaId={}",
                    item.getOrderItemId(), event.getSagaId());
            publishRelease(order, item, event.getTraceId(), "late-reserved-after-failure");
            item.markReleased();
            orderItemRepository.save(item);
            return;
        }

        if (order.allItemsReserved() && order.getOrderStatus() == OrderStatus.PENDING) {
            order.stockReserved();
            sagaMetrics.recordTransition("PENDING", "STOCK_RESERVED");
            publishPaymentRequested(order, event.getTraceId());
        }
    }

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

        for (OrderItemEntity it : order.getOrderItems()) {
            if (it.getReservationStatus() == OrderItemReservationStatus.RESERVED) {
                publishRelease(order, it, event.getTraceId(), "partial-reserve-failed");
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

    private void publishPaymentRequested(OrderEntity order, String traceId) {
        PaymentRequestedEvent paymentEvent = PaymentRequestedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(SagaId.from(order.getSagaId()))
                .aggregateId(AggregateId.from(order.getOrderId()))
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .build();
        paymentEvent.setTraceId(traceId);

        jsonUtil.toJson(paymentEvent).ifPresentOrElse(
                json -> kafkaPublisher.send(
                        paymentRequestedTopic,
                        order.getSagaId().toString(),
                        order.getSagaId().toString(),
                        traceId,
                        json
                ),
                () -> log.error("payment-requested 직렬화 실패. sagaId={}", order.getSagaId())
        );
    }

    private void publishRelease(OrderEntity order, OrderItemEntity item, String traceId, String reason) {
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
                        order.getSagaId().toString(),
                        traceId,
                        json
                ),
                () -> log.error("stock-release-requested 직렬화 실패. orderItemId={}", item.getOrderItemId())
        );
    }
}
