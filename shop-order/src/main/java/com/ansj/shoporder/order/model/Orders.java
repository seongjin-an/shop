package com.ansj.shoporder.order.model;

import com.ansj.shoporder.order.entity.OrderEntity;
import com.ansj.shoporder.order.entity.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
public class Orders {
    private UUID orderId;
    private UUID sagaId;
    private UUID userId;
    private OrderStatus orderStatus;
    private BigDecimal totalAmount;
    private String deliveryAddress;
    private List<OrderItem> items;

    private Orders(OrderEntity entity) {
        this.orderId = entity.getOrderId();
        this.sagaId = entity.getSagaId();
        this.userId = entity.getUserId();
        this.orderStatus = entity.getOrderStatus();
        this.totalAmount = entity.getTotalAmount();
        this.deliveryAddress = entity.getDeliveryAddress();
        this.items = entity.getOrderItems().stream().map(OrderItem::from).collect(Collectors.toList());
    }

    public static Orders from(OrderEntity entity) {
        return new Orders(entity);
    }
}
