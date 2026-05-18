package com.ansj.shoporder.usecase;

import com.ansj.shoporder.box.service.OutboxEventService;
import com.ansj.shoporder.common.AggregateId;
import com.ansj.shoporder.common.EventId;
import com.ansj.shoporder.common.JsonUtil;
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

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class CreateOrderUseCase {

    @Value("${shop.kafka.topics.stock-reservation-requested.topic}")
    private String stockReservationRequestedTopic;

    private final OrderService orderService;
    private final OutboxEventService outboxEventService;
    private final JsonUtil jsonUtil;
    private final SagaMetrics sagaMetrics;

    public UUID createOrder(CreateOrderRequest request, String traceId) {
        Orders order = orderService.createOrder(request);
        sagaMetrics.recordStarted();
        String sagaIdStr = order.getSagaId().toString();
        MDC.put("sagaId", sagaIdStr);
        MDC.put("traceId", traceId);
        try {
            for (OrderItem item : order.getItems()) {
                saveReservationOutbox(order, item, traceId);
            }
            return order.getOrderId();
        } finally {
            MDC.clear();
        }
    }

    private void saveReservationOutbox(Orders order, OrderItem item, String traceId) {
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

        // 주문 저장과 동일 트랜잭션 내에서 outbox INSERT → Debezium이 Kafka로 발행
        outboxEventService.save(event, stockReservationRequestedTopic,
                item.getProductId().toString());
    }
}
