package com.ansj.shoporder.order.usecase;

import com.ansj.shoporder.common.AggregateId;
import com.ansj.shoporder.common.EventId;
import com.ansj.shoporder.common.JsonUtil;
import com.ansj.shoporder.common.SagaId;
import com.ansj.shoporder.order.dto.CreateOrderRequest;
import com.ansj.shoporder.order.event.OrderCreatedEvent;
import com.ansj.shoporder.order.event.OrderEventItem;
import com.ansj.shoporder.order.model.Orders;
import com.ansj.shoporder.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class CreateOrderUseCase {

    @Value("${shop.kafka.topics.order-created.topic}")
    private String orderCreatedTopic;

    private final OrderService orderService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonUtil jsonUtil;

    /**
     * 주문 생성 + order-created 이벤트 발행.
     *
     * 트랜잭션이 커밋된 이후 Kafka 메시지가 전송되므로,
     * DB 저장은 성공했지만 Kafka 전송이 실패하는 경우를 완전히 배제하려면
     * 추후 Outbox 패턴으로 교체할 것.
     */
    public UUID createOrder(CreateOrderRequest request) {
        Orders order = orderService.createOrder(request);

        List<OrderEventItem> eventItems = order.getItems().stream()
                .map(item -> new OrderEventItem(item.getProductId(), item.getQuantity()))
                .toList();

        OrderCreatedEvent event = new OrderCreatedEvent(
                EventId.newId(),
                SagaId.from(order.getSagaId()),
                AggregateId.from(order.getOrderId()),
                "ORDER",
                LocalDateTime.now(),
                eventItems
        );

        jsonUtil.toJson(event)
                .ifPresentOrElse(
                        json -> kafkaTemplate.send(orderCreatedTopic, order.getSagaId().toString(), json),
                        () -> log.error("order-created 이벤트 직렬화 실패. orderId={}", order.getOrderId())
                );

        return order.getOrderId();
    }
}
