package com.ansj.shoporder.order.model;

import com.ansj.shoporder.order.entity.OrderItemEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class OrderItem {
    private UUID orderItemId;
    private UUID orderId;
    private UUID productId;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;

    private OrderItem(OrderItemEntity entity) {
        this.orderItemId = entity.getOrderItemId();
        this.orderId = entity.getOrderId();
        this.productId = entity.getProductId();
        this.productName = entity.getProductName();
        this.unitPrice = entity.getUnitPrice();
        this.quantity = entity.getQuantity();
    }

    public static OrderItem from(OrderItemEntity entity) {
        return new OrderItem(entity);
    }
}
