package com.ansj.shoppayment.payment.entity;

import com.ansj.shoppayment.common.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "payments")
@Entity
public class PaymentEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID paymentId;

    @Column(name = "saga_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID sagaId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private Long userId;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    // PG 필드 (가짜 PG 응답 시뮬레이션)
    @Column(name = "pg_provider")
    private String pgProvider;

    @Column(name = "pg_transaction_id")
    private String pgTransactionId;

    @Column(name = "pg_auth_code", length = 6)
    private String pgAuthCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "method")
    private PaymentMethod method;

    @Column(name = "card_company")
    private String cardCompany;

    @Column(name = "masked_card_number", length = 19)
    private String maskedCardNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    // ─── 상태 전이 ────────────────────────────────────────────────────────────

    public void complete(String pgTransactionId, String pgAuthCode) {
        validateStatus(PaymentStatus.PENDING);
        this.pgTransactionId = pgTransactionId;
        this.pgAuthCode = pgAuthCode;
        this.status = PaymentStatus.COMPLETED;
        this.approvedAt = LocalDateTime.now();
    }

    public void fail(String failureCode, String failureMessage) {
        validateStatus(PaymentStatus.PENDING);
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.status = PaymentStatus.FAILED;
        this.failedAt = LocalDateTime.now();
    }

    // ─── 내부 헬퍼 ────────────────────────────────────────────────────────────

    private void validateStatus(PaymentStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "잘못된 결제 상태 전이입니다. expected=%s, actual=%s".formatted(expected, this.status)
            );
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.paymentId == null) {
            this.paymentId = UuidUtils.createV7();
        }
        if (this.status == null) {
            this.status = PaymentStatus.PENDING;
        }
        if (this.currency == null) {
            this.currency = "KRW";
        }
        this.requestedAt = LocalDateTime.now();
    }
}
