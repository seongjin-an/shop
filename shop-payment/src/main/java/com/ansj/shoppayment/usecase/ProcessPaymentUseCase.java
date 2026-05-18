package com.ansj.shoppayment.usecase;

import com.ansj.shoppayment.box.service.InboxEventService;
import com.ansj.shoppayment.box.service.OutboxEventService;
import com.ansj.shoppayment.common.*;
import com.ansj.shoppayment.payment.entity.PaymentEntity;
import com.ansj.shoppayment.payment.entity.PaymentMethod;
import com.ansj.shoppayment.payment.entity.PaymentStatus;
import com.ansj.shoppayment.payment.event.inbound.PaymentRequestedEvent;
import com.ansj.shoppayment.payment.event.outbound.PaymentFailedEvent;
import com.ansj.shoppayment.payment.event.outbound.PaymentSuccessEvent;
import com.ansj.shoppayment.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProcessPaymentUseCase {

    private static final Random RANDOM = new Random();
    private static final String[] CARD_COMPANIES = {"SHINHAN", "KB", "HYUNDAI", "SAMSUNG", "LOTTE"};

    @Value("${shop.kafka.topics.payment-success.topic}")
    private String paymentSuccessTopic;

    @Value("${shop.kafka.topics.payment-failed.topic}")
    private String paymentFailedTopic;

    private final PaymentService paymentService;
    private final InboxEventService inboxEventService;
    private final OutboxEventService outboxEventService;
    private final JsonUtil jsonUtil;

    @Transactional
    public void process(PaymentRequestedEvent event) {
        if (inboxEventService.existsByEventId(event.getEventId())) {
            log.info("중복 이벤트 무시. eventId={}", event.getEventId());
            return;
        }

        PaymentEntity payment = PaymentEntity.builder()
                .sagaId(event.getSagaId().id())
                .userId(event.getUserId())
                .orderId(event.getAggregateId().id())
                .amount(event.getTotalAmount())
                .pgProvider("FAKE_PG")
                .method(PaymentMethod.CARD)
                .cardCompany(CARD_COMPANIES[RANDOM.nextInt(CARD_COMPANIES.length)])
                .maskedCardNumber("1234-****-****-" + String.format("%04d", RANDOM.nextInt(10000)))
                .status(PaymentStatus.PENDING)
                .build();

        String sagaIdStr = event.getSagaId().toString();
        boolean success = RANDOM.nextInt(10) > 0;

        if (success) {
            String pgTransactionId = UUID.randomUUID().toString();
            String pgAuthCode = String.format("%06d", RANDOM.nextInt(1000000));
            payment.complete(pgTransactionId, pgAuthCode);
            paymentService.save(payment);
            inboxEventService.createInboxEvent(event);

            PaymentSuccessEvent successEvent = buildSuccessEvent(event);
            // 결제 저장 + inbox + outbox 를 동일 트랜잭션으로 커밋 → Debezium이 Kafka 발행 보장
            outboxEventService.save(successEvent, paymentSuccessTopic, sagaIdStr);
            log.info("결제 성공. sagaId={}, pgTransactionId={}", event.getSagaId(), pgTransactionId);
        } else {
            payment.fail("INSUFFICIENT_BALANCE", "잔액이 부족합니다 (시뮬레이션)");
            paymentService.save(payment);
            inboxEventService.createInboxEvent(event);

            PaymentFailedEvent failedEvent = buildFailedEvent(event, "INSUFFICIENT_BALANCE");
            outboxEventService.save(failedEvent, paymentFailedTopic, sagaIdStr);
            log.info("결제 실패. sagaId={}", event.getSagaId());
        }
    }

    private PaymentSuccessEvent buildSuccessEvent(PaymentRequestedEvent event) {
        PaymentSuccessEvent successEvent = PaymentSuccessEvent.builder()
                .eventId(EventId.newId())
                .sagaId(event.getSagaId())
                .aggregateId(event.getAggregateId())
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .build();
        successEvent.setTraceId(event.getTraceId());
        return successEvent;
    }

    private PaymentFailedEvent buildFailedEvent(PaymentRequestedEvent event, String reason) {
        PaymentFailedEvent failedEvent = PaymentFailedEvent.builder()
                .eventId(EventId.newId())
                .sagaId(event.getSagaId())
                .aggregateId(event.getAggregateId())
                .aggregateType("ORDER")
                .occurredAt(LocalDateTime.now())
                .reason(reason)
                .build();
        failedEvent.setTraceId(event.getTraceId());
        return failedEvent;
    }
}
