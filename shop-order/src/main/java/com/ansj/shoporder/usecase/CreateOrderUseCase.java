package com.ansj.shoporder.usecase;

import com.ansj.shoporder.common.AggregateId;
import com.ansj.shoporder.common.EventId;
import com.ansj.shoporder.common.JsonUtil;
import com.ansj.shoporder.common.SagaAwareKafkaPublisher;
import com.ansj.shoporder.common.SagaId;
import com.ansj.shoporder.metrics.SagaMetrics;
import com.ansj.shoporder.order.dto.CreateOrderRequest;
import com.ansj.shoporder.order.event.outbound.StockReservationRequestedEvent;
import com.ansj.shoporder.order.model.OrderItem;
import com.ansj.shoporder.order.model.Orders;
import com.ansj.shoporder.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 생성 + per-item {@code stock-reservation-requested} 이벤트 fan-out.
 *
 * <p>변경 이전: 단일 {@code order-created} 이벤트(key=sagaId) 를 발행했다.
 * 이 방식은 서로 다른 주문이 동일한 상품을 예약할 때 각기 다른 파티션/스레드로
 * 흘러가 StockEntity 에 대한 낙관적 락 경합이 발생했다.
 *
 * <p>변경 이후: 주문 1건의 각 아이템에 대해 개별 이벤트를 발행한다.
 * <ul>
 *   <li>topic = {@code stock-reservation-requested}</li>
 *   <li>key   = productId (UUID.toString())</li>
 *   <li>baggage.saga.id = sagaId (Tempo 트레이스 상관관계 보존용)</li>
 * </ul>
 *
 * <p>동일 productId 이벤트는 항상 같은 파티션으로 라우팅되므로
 * shop-stock 쪽에서 동일 StockEntity 에 대한 동시 write 가 사라진다(single-writer-per-aggregate).
 */
@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class CreateOrderUseCase {

    @Value("${shop.kafka.topics.stock-reservation-requested.topic}")
    private String stockReservationRequestedTopic;

    private final OrderService orderService;
    private final SagaAwareKafkaPublisher kafkaPublisher;
    private final JsonUtil jsonUtil;
    private final SagaMetrics sagaMetrics;

    public UUID createOrder(CreateOrderRequest request) {
        Orders order = orderService.createOrder(request);
        sagaMetrics.recordStarted();
        String sagaIdStr = order.getSagaId().toString();
        MDC.put("sagaId", sagaIdStr);
        try {
            for (OrderItem item : order.getItems()) {
                publishReservationRequested(order, item, sagaIdStr);
            }
            return order.getOrderId();
        } finally {
            MDC.clear();
        }
    }

    private void publishReservationRequested(Orders order, OrderItem item, String sagaIdStr) {
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

        jsonUtil.toJson(event).ifPresentOrElse(
                json -> kafkaPublisher.send(
                        stockReservationRequestedTopic,
                        item.getProductId().toString(),  // partition key = productId
                        sagaIdStr,
                        json
                ),
                () -> log.error("stock-reservation-requested 직렬화 실패. orderItemId={}", item.getOrderItemId())
        );
    }
}
