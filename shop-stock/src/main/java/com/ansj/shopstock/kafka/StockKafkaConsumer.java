package com.ansj.shopstock.kafka;

import com.ansj.shopstock.common.JsonUtil;
import com.ansj.shopstock.stock.event.inbound.ProductCreatedEvent;
import com.ansj.shopstock.stock.event.inbound.StockConfirmRequestedEvent;
import com.ansj.shopstock.stock.event.inbound.StockReleaseRequestedEvent;
import com.ansj.shopstock.stock.event.inbound.StockReservationRequestedEvent;
import com.ansj.shopstock.usecase.CompensateStockUseCase;
import com.ansj.shopstock.usecase.ReserveStockUseCase;
import com.ansj.shopstock.usecase.StockUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * 재고 서비스 Kafka 소비자.
 *
 * <p>per-item 리스너(key=productId)만 유지.
 * product-created 는 별도의 관리 토픽이라 order-level 로 유지.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StockKafkaConsumer {

    private final StockUseCase stockUseCase;
    private final ReserveStockUseCase reserveStockUseCase;
    private final CompensateStockUseCase compensateStockUseCase;
    private final JsonUtil jsonUtil;

    // ─── product ─────────────────────────────────────────────────────────────

    @KafkaListener(
            topics = "${shop.kafka.topics.product-created.topic}",
            groupId = "${shop.kafka.topics.product-created.group-id}",
            concurrency = "${shop.kafka.topics.product-created.concurrency}"
    )
    public void onProductCreated(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), ProductCreatedEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        stockUseCase.processIncreaseStockEvent(event);
                    });
        } catch (Exception e) {
            log.error("product-created 처리 중 오류. cause: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
            acknowledgment.acknowledge();
        }
    }

    // ─── per-item 신규 흐름 ──────────────────────────────────────────────────

    @KafkaListener(
            topics = "${shop.kafka.topics.stock-reservation-requested.topic}",
            groupId = "${shop.kafka.topics.stock-reservation-requested.group-id}",
            concurrency = "${shop.kafka.topics.stock-reservation-requested.concurrency}"
    )
    public void onStockReservationRequested(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), StockReservationRequestedEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        reserveStockUseCase.processReservationRequested(event);
                    });
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("stock-reservation-requested 처리 중 오류. topic={}, offset={}, cause={}",
                    record.topic(), record.offset(), e.getMessage(), e);
            throw e; // DefaultErrorHandler → 재시도 → DLT
        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(
            topics = "${shop.kafka.topics.stock-confirm-requested.topic}",
            groupId = "${shop.kafka.topics.stock-confirm-requested.group-id}",
            concurrency = "${shop.kafka.topics.stock-confirm-requested.concurrency}"
    )
    public void onStockConfirmRequested(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), StockConfirmRequestedEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        compensateStockUseCase.onStockConfirmRequested(event);
                    });
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("stock-confirm-requested 처리 중 오류. topic={}, offset={}, cause={}",
                    record.topic(), record.offset(), e.getMessage(), e);
            throw e;
        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(
            topics = "${shop.kafka.topics.stock-release-requested.topic}",
            groupId = "${shop.kafka.topics.stock-release-requested.group-id}",
            concurrency = "${shop.kafka.topics.stock-release-requested.concurrency}"
    )
    public void onStockReleaseRequested(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            jsonUtil.fromJson(record.value(), StockReleaseRequestedEvent.class)
                    .ifPresent(event -> {
                        MDC.put("sagaId", event.getSagaId().toString());
                        compensateStockUseCase.onStockReleaseRequested(event);
                    });
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("stock-release-requested 처리 중 오류. topic={}, offset={}, cause={}",
                    record.topic(), record.offset(), e.getMessage(), e);
            throw e;
        } finally {
            MDC.clear();
        }
    }

    // ─── 레거시 order-level 리스너는 제거됨 ──────────────────────────────────
    // shop-order 가 order-created / payment-success(→stock) / order-canceled 를
    // 'order-level' 로 더 이상 발행하지 않기 때문에,
    //  - onOrderCreatedLegacy
    //  - onPaymentSuccessLegacy  (inbox 에 ORDER_CREATED 페이로드가 없어 항상 실패)
    //  - onOrderCancelledLegacy
    // 세 리스너는 완전히 제거했다. 토픽 자체는 유지하되 shop-stock 이 구독하지 않음.
    // payment-success 는 이제 shop-order 가 구독 → per-item stock-confirm-requested fan-out.
}
