package com.ansj.shopstock.usecase;

import com.ansj.shopstock.stock.event.inbound.StockConfirmRequestedEvent;
import com.ansj.shopstock.stock.event.inbound.StockReleaseRequestedEvent;
import com.ansj.shopstock.stock.event.inbound.StockReservationRequestedEvent;
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
    private final ReserveStockUseCase reserveStockUseCase;

    // ─── per-item 신규 DLT 재처리 ────────────────────────────────────────────

    @Retryable(retryFor = Exception.class, maxAttempts = 10,
            backoff = @Backoff(delay = 3_000, multiplier = 1.5, maxDelay = 30_000))
    public void retryStockReservationRequested(StockReservationRequestedEvent event) {
        log.debug("[DLT-Retry] stock-reservation-requested 재시도. sagaId={}, productId={}",
                event.getSagaId(), event.getProductId());
        reserveStockUseCase.processReservationRequested(event);
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 10,
            backoff = @Backoff(delay = 3_000, multiplier = 1.5, maxDelay = 30_000))
    public void retryStockConfirmRequested(StockConfirmRequestedEvent event) {
        log.debug("[DLT-Retry] stock-confirm-requested 재시도. sagaId={}, productId={}",
                event.getSagaId(), event.getProductId());
        compensateStockUseCase.onStockConfirmRequested(event);
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 10,
            backoff = @Backoff(delay = 3_000, multiplier = 1.5, maxDelay = 30_000))
    public void retryStockReleaseRequested(StockReleaseRequestedEvent event) {
        log.debug("[DLT-Retry] stock-release-requested 재시도. sagaId={}, productId={}",
                event.getSagaId(), event.getProductId());
        compensateStockUseCase.onStockReleaseRequested(event);
    }

    // ─── 레거시 DLT 재처리는 제거됨 ─────────────────────────────────────────
    // order-level payment-success / order-canceled DLT 는 더 이상 신규 메시지가
    // 생성되지 않는다. 잔여 메시지가 있다면 Kafka UI 에서 수동 정리.
}
