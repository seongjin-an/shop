package com.ansj.shoporder.order.dto;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class CreateOrderItemRequest {
    private UUID productId;
    private String productName;
    private BigDecimal unitPrice;
    private int quantity;
}
