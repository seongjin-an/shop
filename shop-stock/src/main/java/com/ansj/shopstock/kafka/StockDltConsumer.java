package com.ansj.shopstock.kafka;

import com.ansj.shopstock.common.JsonUtil;
import com.ansj.shopstock.stock.event.inbound.OrderCancelledEvent;
import com.ansj.shopstock.stock.event.inbound.PaymentSuccessEvent;
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

    @KafkaListener(
            topics = "${shop.kafka.topics.payment-success-dlt.topic}",
            groupId = "${shop.kafka.topics.payment-success-dlt.group-id}",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void onPaymentSuccessDlt(ConsumerRecord<String, String> record) {
        log.warn("[DLT] payment-success 재처리 시작. topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        jsonUtil.fromJson(record.value(), PaymentSuccessEvent.class)
                .ifPresentOrElse(
                        event -> {
                            MDC.put("sagaId", event.getSagaId().toString());
                            try {
                                // 3초~30초 간격으로 최대 10회 재시도 (DltRetryService)
                                dltRetryService.retryPaymentSuccess(event);
                                log.info("[DLT] payment-success 재처리 성공. sagaId={}", event.getSagaId());
                            } catch (Exception e) {
                                // 10회 모두 소진 → 수동 개입 필요
                                // TODO: Slack / PagerDuty 알람 연동
                                log.error("[DLT] payment-success 재처리 최종 실패 — 수동 개입 필요. " +
                                          "sagaId={}, cause={}", event.getSagaId(), e.getMessage(), e);
                            } finally {
                                MDC.clear();
                            }
                        },
                        () -> log.error("[DLT] payment-success 역직렬화 실패. payload={}", record.value())
                );
    }

    @KafkaListener(
            topics = "${shop.kafka.topics.order-cancelled-dlt.topic}",
            groupId = "${shop.kafka.topics.order-cancelled-dlt.group-id}",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void onOrderCancelledDlt(ConsumerRecord<String, String> record) {
        log.warn("[DLT] order-canceled 재처리 시작. topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        jsonUtil.fromJson(record.value(), OrderCancelledEvent.class)
                .ifPresentOrElse(
                        event -> {
                            MDC.put("sagaId", event.getSagaId().toString());
                            try {
                                // 3초~30초 간격으로 최대 10회 재시도 (DltRetryService)
                                dltRetryService.retryOrderCancelled(event);
                                log.info("[DLT] order-canceled 재처리 성공. sagaId={}", event.getSagaId());
                            } catch (Exception e) {
                                // 10회 모두 소진 → 수동 개입 필요
                                // TODO: Slack / PagerDuty 알람 연동
                                log.error("[DLT] order-canceled 재처리 최종 실패 — 수동 개입 필요. " +
                                          "sagaId={}, cause={}", event.getSagaId(), e.getMessage(), e);
                            } finally {
                                MDC.clear();
                            }
                        },
                        () -> log.error("[DLT] order-canceled 역직렬화 실패. payload={}", record.value())
                );
    }
}
