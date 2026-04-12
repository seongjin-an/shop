package com.ansj.shopstock.usecase;

import com.ansj.shopstock.stock.event.inbound.OrderCancelledEvent;
import com.ansj.shopstock.stock.event.inbound.PaymentSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * DLT 재처리 전용 재시도 서비스.
 *
 * <p>메인 컨슈머(@Retryable maxAttempts=5, maxDelay=500ms)와 달리
 * 훨씬 긴 지연으로 재시도한다. DLT 메시지는 스트레스 테스트 중 경합이
 * 극심할 때 유입되는 경우가 많으므로, 충분한 지연을 두어 경합이 가라앉을
 * 때까지 기다린다.
 *
 * <p>Spring AOP 제약(@Retryable은 동일 클래스 내 자기 호출에 미적용)으로
 * {@link com.ansj.shopstock.kafka.StockDltConsumer}와 별도 빈으로 분리.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DltRetryService {

    private final CompensateStockUseCase compensateStockUseCase;

    /**
     * payment-success DLT 재처리.
     * 3초 간격으로 최대 10회 재시도 (최대 대기 약 30초).
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 10,
            backoff = @Backoff(delay = 3_000, multiplier = 1.5, maxDelay = 30_000)
    )
    public void retryPaymentSuccess(PaymentSuccessEvent event) {
        log.debug("[DLT-Retry] payment-success 재시도 중. sagaId={}", event.getSagaId());
        compensateStockUseCase.onPaymentSuccess(event);
    }

    /**
     * order-canceled DLT 재처리.
     * 3초 간격으로 최대 10회 재시도 (최대 대기 약 30초).
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 10,
            backoff = @Backoff(delay = 3_000, multiplier = 1.5, maxDelay = 30_000)
    )
    public void retryOrderCancelled(OrderCancelledEvent event) {
        log.debug("[DLT-Retry] order-canceled 재시도 중. sagaId={}", event.getSagaId());
        compensateStockUseCase.onOrderCancelled(event);
    }
}
