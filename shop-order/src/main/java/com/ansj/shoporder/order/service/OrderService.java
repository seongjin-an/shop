package com.ansj.shoporder.order.service;

import com.ansj.shoporder.common.AggregateId;
import com.ansj.shoporder.common.EventId;
import com.ansj.shoporder.common.SagaId;
import com.ansj.shoporder.order.dto.CreateOrderRequest;
import com.ansj.shoporder.order.entity.OrderEntity;
import com.ansj.shoporder.order.entity.OrderItemEntity;
import com.ansj.shoporder.order.event.OrderCreatedEvent;
import com.ansj.shoporder.order.event.OrderEventItem;
import com.ansj.shoporder.order.model.Orders;
import com.ansj.shoporder.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Orders createOrder(CreateOrderRequest request) {
        List<OrderItemEntity> items = request.getItems().stream()
                .map(item -> OrderItemEntity.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        BigDecimal totalAmount = items.stream()
                .map(OrderItemEntity::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OrderEntity order = OrderEntity.builder()
                .userId(request.getUserId())
                .deliveryAddress(request.getDeliveryAddress())
                .totalAmount(totalAmount)
                .build();

        items.forEach(order::addItem);

        return Orders.from(orderRepository.save(order));
    }

    public Orders getOrder(UUID orderId) {
        OrderEntity orderEntity = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문이 존재하지 않습니다. orderId=" + orderId));
        return Orders.from(orderEntity);
    }

    public OrderEntity getOrderBySagaId(UUID sagaId) {
        return orderRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("주문이 존재하지 않습니다. sagaId=" + sagaId));
    }
}
