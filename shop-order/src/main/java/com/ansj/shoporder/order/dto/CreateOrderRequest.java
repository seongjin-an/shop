package com.ansj.shoporder.order.dto;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class CreateOrderRequest {
    private Long userId;
    private String deliveryAddress;
    private List<CreateOrderItemRequest> items;
}
