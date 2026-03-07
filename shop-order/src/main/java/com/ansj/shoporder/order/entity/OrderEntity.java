package com.ansj.shoporder.order.entity;

import com.ansj.shoporder.common.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_user_id_created", columnList = "user_id, created_at DESC"),
                @Index(name = "idx_orders_saga_id", columnList = "saga_id"),
                @Index(name = "idx_orders_status", columnList = "order_status")
        }
)
@Entity
public class OrderEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID orderId;

    /**
     * Saga 흐름에서 이벤트 상관관계를 추적하는 ID.
     * 여러 서비스에 걸친 이벤트가 동일한 sagaId를 공유한다.
     */
    @Column(name = "saga_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID sagaId;

    /** 사용자 서비스의 사용자 ID. FK 강제하지 않음 */
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    /** 주문 시점의 총 금액 스냅샷 */
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemEntity> orderItems = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ─── 연관관계 편의 메서드 ───────────────────────────────────────────────────

    public void addItem(OrderItemEntity item) {
        item.assignOrder(this);
        this.orderItems.add(item);
    }

    // ─── Saga 상태 전이 ────────────────────────────────────────────────────────

    /**
     * stock-reserved 이벤트 수신 시.
     * PENDING → STOCK_RESERVED
     */
    public void stockReserved() {
        validateStatus(OrderStatus.PENDING);
        this.orderStatus = OrderStatus.STOCK_RESERVED;
    }

    /**
     * stock-reserve-failed 이벤트 수신 시.
     * PENDING → STOCK_FAILED
     */
    public void stockFailed() {
        validateStatus(OrderStatus.PENDING);
        this.orderStatus = OrderStatus.STOCK_FAILED;
    }

    /**
     * payment-success 이벤트 수신 시.
     * STOCK_RESERVED → COMPLETED
     */
    public void paymentCompleted() {
        validateStatus(OrderStatus.STOCK_RESERVED);
        this.orderStatus = OrderStatus.COMPLETED;
    }

    /**
     * payment-failed 이벤트 수신 시.
     * STOCK_RESERVED → PAYMENT_FAILED
     * 이후 order-cancelled 이벤트를 발행해 재고 보상 트랜잭션을 시작한다.
     */
    public void paymentFailed() {
        validateStatus(OrderStatus.STOCK_RESERVED);
        this.orderStatus = OrderStatus.PAYMENT_FAILED;
    }

    /**
     * stock-reservation-cancelled 이벤트 수신 시 (보상 완료).
     * PAYMENT_FAILED → CANCELLED
     */
    public void compensationCompleted() {
        validateStatus(OrderStatus.PAYMENT_FAILED);
        this.orderStatus = OrderStatus.CANCELLED;
    }

    // ─── 내부 헬퍼 ────────────────────────────────────────────────────────────

    private void validateStatus(OrderStatus expected) {
        if (this.orderStatus != expected) {
            throw new IllegalStateException(
                    "잘못된 주문 상태 전이입니다. expected=%s, actual=%s".formatted(expected, this.orderStatus)
            );
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.orderId == null) {
            this.orderId = UuidUtils.createV7();
        }
        if (this.sagaId == null) {
            this.sagaId = UuidUtils.createV7();
        }
        this.orderStatus = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
