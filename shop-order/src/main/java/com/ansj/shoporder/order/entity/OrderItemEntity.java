package com.ansj.shoporder.order.entity;

import com.ansj.shoporder.common.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "order_item")
@Entity
public class OrderItemEntity {

    @Id
    private UUID orderItemId;

    /**
     * FK 관리는 @ManyToOne 이 담당.
     * orderId 필드는 부모 엔티티 로딩 없이 FK 값을 읽기 위한 읽기 전용 매핑.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "order_id", insertable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    /** 상품 서비스의 상품 ID. FK 강제하지 않음 */
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    /** 주문 시점의 상품명 스냅샷 */
    @Column(name = "product_name", nullable = false)
    private String productName;

    /** 주문 시점의 단가 스냅샷 */
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * 아이템 단위 재고 라이프사이클 상태.
     * Aggregator 가 sagaId 당 전체 아이템이 RESERVED 도달했는지 판정할 때 사용.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_status", nullable = false, length = 20)
    @Builder.Default
    private OrderItemReservationStatus reservationStatus = OrderItemReservationStatus.PENDING;

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    void assignOrder(OrderEntity order) {
        this.order = order;
    }

    // ─── 상태 전이 (per-item) ───────────────────────────────────────────────────

    public void markReserved() {
        validateTransition(OrderItemReservationStatus.PENDING, OrderItemReservationStatus.RESERVED);
        this.reservationStatus = OrderItemReservationStatus.RESERVED;
    }

    public void markFailed() {
        validateTransition(OrderItemReservationStatus.PENDING, OrderItemReservationStatus.FAILED);
        this.reservationStatus = OrderItemReservationStatus.FAILED;
    }

    public void markConfirmed() {
        validateTransition(OrderItemReservationStatus.RESERVED, OrderItemReservationStatus.CONFIRMED);
        this.reservationStatus = OrderItemReservationStatus.CONFIRMED;
    }

    public void markReleased() {
        // PENDING(응답 대기 중) / RESERVED(이미 예약) 양쪽에서 모두 보상 가능
        if (this.reservationStatus != OrderItemReservationStatus.PENDING
                && this.reservationStatus != OrderItemReservationStatus.RESERVED) {
            throw new IllegalStateException(
                    "RELEASED 전이 불가. current=" + this.reservationStatus
            );
        }
        this.reservationStatus = OrderItemReservationStatus.RELEASED;
    }

    private void validateTransition(OrderItemReservationStatus expected, OrderItemReservationStatus next) {
        if (this.reservationStatus != expected) {
            throw new IllegalStateException(
                    "잘못된 아이템 상태 전이. expected=%s, actual=%s, next=%s"
                            .formatted(expected, this.reservationStatus, next)
            );
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.orderItemId == null) {
            this.orderItemId = UuidUtils.createV7();
        }
        if (this.reservationStatus == null) {
            this.reservationStatus = OrderItemReservationStatus.PENDING;
        }
    }
}
