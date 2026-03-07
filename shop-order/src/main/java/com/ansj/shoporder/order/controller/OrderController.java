package com.ansj.shoporder.order.controller;

import com.ansj.shoporder.order.dto.CreateOrderRequest;
import com.ansj.shoporder.order.model.Orders;
import com.ansj.shoporder.order.service.OrderService;
import com.ansj.shoporder.order.usecase.CreateOrderUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RequiredArgsConstructor
@RequestMapping("/orders")
@RestController
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Void> createOrder(@RequestBody CreateOrderRequest request) {
        UUID orderId = createOrderUseCase.createOrder(request);
        return ResponseEntity.created(URI.create("/orders/" + orderId)).build();
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Orders> getOrder(@PathVariable UUID orderId) {
        Orders order = orderService.getOrder(orderId);
        return ResponseEntity.ok(order);
    }
}
