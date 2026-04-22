package com.ansj.shopstock.kafka;

import com.ansj.shopstock.common.JsonUtil;
import com.ansj.shopstock.stock.event.inbound.StockConfirmRequestedEvent;
import com.ansj.shopstock.stock.event.inbound.StockReleaseRequestedEvent;
import com.ansj.shopstock.stock.event.inbound.StockReservationRequestedEvent;
import com.ansj.shopstock.usecase.DltRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Dead Letter Topic 컨슈머.
 *
 * <p>메인 컨슈머의 모든 재시도(@Retryable × DefaultErrorHandler)를 소진하고도
 * 실패한 메시지가 -DLT 토픽으로 흘러들어온다.
 *
 * <p>재시도는 {@link DltRetryService}에 위임한다.
 * DltRetryService는 3초~30초 간격으로 최대 10회 재시도하여,
 * 스트레스 테스트가 끝나 경합이 가라앉은 뒤 자연스럽게 성공하도록 설계됐다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StockDltConsumer {

    private final DltRetryService dltRetryService;
    private final JsonUtil jsonUtil;

    // ─── per-item 신규 DLT 리스너 ────────────────────────────────────────────

    @KafkaListener(
            topics = "${shop.kafka.topics.stock-reservation-requested-dlt.topic}",
            groupId = "${shop.kafka.topics.stock-reservation-requested-dlt.group-id}",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void onStockReservationRequestedDlt(ConsumerRecord<String, String> record) {
        log.warn("[DLT] stock-reservation-requested 재처리 시작. topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());
        jsonUtil.fromJson(record.value(), StockReservationRequestedEvent.class)
                .ifPresentOrElse(
                        event -> {
                            MDC.put("sagaId", event.getSagaId().toString());
                            try {
                                dltRetryService.retryStockReservationRequested(event);
                                log.info("[DLT] stock-reservation-requested 재처리 성공. sagaId={}, productId={}",
                                        event.getSagaId(), event.getProductId());
                            } catch (Exception e) {
                                log.error("[DLT] stock-reservation-requested 재처리 최종 실패 — 수동 개입 필요. " +
                                        "sagaId={}, productId={}, cause={}",
                                        event.getSagaId(), event.getProductId(), e.getMessage(), e);
                            } finally {
                                MDC.clear();
                            }
                        },
                        () -> log.error("[DLT] stock-reservation-requested 역직렬화 실패. payload={}", record.value())
                );
    }

    @KafkaListener(
            topics = "${shop.kafka.topics.stock-confirm-requested-dlt.topic}",
            groupId = "${shop.kafka.topics.stock-confirm-requested-dlt.group-id}",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void onStockConfirmRequestedDlt(ConsumerRecord<String, String> record) {
        log.warn("[DLT] stock-confirm-requested 재처리 시작. topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());
        jsonUtil.fromJson(record.value(), StockConfirmRequestedEvent.class)
                .ifPresentOrElse(
                        event -> {
                            MDC.put("sagaId", event.getSagaId().toString());
                            try {
                                dltRetryService.retryStockConfirmRequested(event);
                                log.info("[DLT] stock-confirm-requested 재처리 성공. sagaId={}, productId={}",
                                        event.getSagaId(), event.getProductId());
                            } catch (Exception e) {
                                log.error("[DLT] stock-confirm-requested 재처리 최종 실패 — 수동 개입 필요. " +
                                        "sagaId={}, productId={}, cause={}",
                                        event.getSagaId(), event.getProductId(), e.getMessage(), e);
                            } finally {
                                MDC.clear();
                            }
                        },
                        () -> log.error("[DLT] stock-confirm-requested 역직렬화 실패. payload={}", record.value())
                );
    }

    @KafkaListener(
            topics = "${shop.kafka.topics.stock-release-requested-dlt.topic}",
            groupId = "${shop.kafka.topics.stock-release-requested-dlt.group-id}",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void onStockReleaseRequestedDlt(ConsumerRecord<String, String> record) {
        log.warn("[DLT] stock-release-requested 재처리 시작. topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());
        jsonUtil.fromJson(record.value(), StockReleaseRequestedEvent.class)
                .ifPresentOrElse(
                        event -> {
                            MDC.put("sagaId", event.getSagaId().toString());
                            try {
                                dltRetryService.retryStockReleaseRequested(event);
                                log.info("[DLT] stock-release-requested 재처리 성공. sagaId={}, productId={}",
                                        event.getSagaId(), event.getProductId());
                            } catch (Exception e) {
                                log.error("[DLT] stock-release-requested 재처리 최종 실패 — 수동 개입 필요. " +
                                        "sagaId={}, productId={}, cause={}",
                                        event.getSagaId(), event.getProductId(), e.getMessage(), e);
                            } finally {
                                MDC.clear();
                            }
                        },
                        () -> log.error("[DLT] stock-release-requested 역직렬화 실패. payload={}", record.value())
                );
    }

    // ─── 레거시 DLT 리스너는 제거됨 ──────────────────────────────────────────
    // shop-stock 은 더 이상 order-level payment-success / order-canceled 를
    // 직접 소비하지 않으므로, 해당 DLT 로 들어올 메시지도 구조적으로 존재하지 않는다.
    // 기존에 쌓여있던 DLT 메시지가 있다면 Kafka UI 에서 수동으로 삭제하거나 무시.
}
