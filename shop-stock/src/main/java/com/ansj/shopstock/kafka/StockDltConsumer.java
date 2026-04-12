package com.ansj.shopstock.kafka;

import com.ansj.shopstock.common.JsonUtil;
import com.ansj.shopstock.stock.event.inbound.OrderCancelledEvent;
import com.ansj.shopstock.stock.event.inbound.PaymentSuccessEvent;
import com.ansj.shopstock.usecase.CompensateStockUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Dead Letter Topic 컨슈머.
 *
 * <p>메인 컨슈머({@link StockKafkaConsumer})에서 모든 재시도(@Retryable × DefaultErrorHandler)를
 * 소진하고도 처리에 실패한 메시지가 .DLT 토픽으로 흘러들어온다.
 *
 * <p>재처리 전략
 * <ul>
 *   <li>부하 분산 효과: 메인 컨슈머에서 실패할 당시보다 DB 경합이 줄어든 상태에서 1회 더 시도한다.</li>
 *   <li>idempotency: {@link CompensateStockUseCase} 내부 inbox dedup 이 동일 eventId 의 중복 처리를 막는다.</li>
 *   <li>DLT → DLT 루프 없음: dltKafkaListenerContainerFactory 에는 ErrorHandler/DLT 설정이 없어
 *       재처리 실패 시 로그만 남기고 종료한다. 이후엔 수동 개입(Kafka CLI replay 등)이 필요하다.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StockDltConsumer {

    private final CompensateStockUseCase compensateStockUseCase;
    private final JsonUtil jsonUtil;

    /**
     * payment-success.DLT 재처리.
     * 성공 시 reservedQuantity 차감(예약 확정), 실패 시 수동 개입 필요.
     */
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
                                compensateStockUseCase.onPaymentSuccess(event);
                                log.info("[DLT] payment-success 재처리 성공. sagaId={}", event.getSagaId());
                            } catch (Exception e) {
                                // 여기서 실패하면 자동 재시도 없음 → 수동 개입 필요
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

    /**
     * order-canceled.DLT 재처리.
     * 성공 시 reservedQuantity 복구(보상 트랜잭션), 실패 시 수동 개입 필요.
     */
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
                                compensateStockUseCase.onOrderCancelled(event);
                                log.info("[DLT] order-canceled 재처리 성공. sagaId={}", event.getSagaId());
                            } catch (Exception e) {
                                // 여기서 실패하면 자동 재시도 없음 → 수동 개입 필요
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
