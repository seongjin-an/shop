package com.ansj.shoporder.usecase;

import com.ansj.shoporder.order.dto.CreateOrderRequest;
import com.ansj.shoporder.order.model.Orders;
import com.ansj.shoporder.order.service.OrderService;
import com.ansj.shoporder.saga.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class CreateOrderUseCase {

    private final OrderService orderService;
    private final OrderSagaOrchestrator orchestrator;

    public UUID createOrder(CreateOrderRequest request, String traceId) {
        Orders order = orderService.createOrder(request);
        MDC.put("sagaId", order.getSagaId().toString());
        MDC.put("traceId", traceId);
        try {
            // Orchestrator가 첫 번째 명령(재고 예약)을 결정해서 outbox에 저장
            orchestrator.start(order, traceId);
            return order.getOrderId();
        } finally {
            MDC.clear();
        }
    }
}
