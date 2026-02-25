package com.ansj.shopstock.stock.entity;

import com.ansj.shopstock.common.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
        name = "stock",
        indexes = {
                @Index(name = "idx_stock_product_id", columnList = "product_id")
        }
)
@Entity
public class StockEntity {
    @Id
    private UUID stockId;

    /**
     * 상품 ID (Product Service와 분리 가능하도록 FK 강제하지 않음)
     */
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    /**
     * 현재 재고 수량
     */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * 가예약 수량(결제 대기 중인 수량)
     */
    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;

    /**
     * 판매 가능 여부
     */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    /**
     * 낙관적 락 (동시성 제어)
     */
    @Version
    private Long version;

    /**
     * 생성/수정 시간
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 소프트 삭제
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    ///////////////////////////////////////////////////
    public void increase(int amount) {
        validateAmount(amount);
        this.quantity += amount;
    }

    public void decrease(int amount) {
        validateAmount(amount);
        if (this.quantity < amount) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.quantity -= amount;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.active = false;
    }

    private void validateAmount(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다.");
        }
    }

    /**
     * 1단계: 재고 예약(Order Created 이벤트 수신 시)
     * 가용 재고(quantity)를 줄이고, 예약 재고(reservedQuantity)를 늘린다.
     */
    public void reserve(int amount) {
        validateAmount(amount);
        if (this.quantity < amount) {
            throw new IllegalStateException("가용 재고가 부족하여 예약할 수 없습니다.");
        }
        this.quantity -= amount;
        this.reservedQuantity += amount;
    }

    /**
     * 2단계(성공): 예약 확정(Payment Processed 이벤트 수신 시)
     * 예약된 수량을 완전히 소거한다.(이미 quantity 에서 빠졌으므로 reserved 만 차감)
     */
    public void confirmReservation(int amount) {
        validateAmount(amount);
        if (this.reservedQuantity < amount) {
            throw new IllegalStateException("확정할 예약 재고가 부족합니다.");
        }
        this.reservedQuantity -= amount;
    }

    /**
     * 2단계(실패): 예약 취소/보상 (Payment Failed 이벤트 수신 시)
     * 예약된 수량을 다시 가용 재고(quantity)로 돌려준다.
     */
    public void cancelReservation(int amount) {
        validateAmount(amount);
        if (this.reservedQuantity < amount) {
            throw new IllegalStateException("취소할 예약 재고가 부족합니다.");
        }
        this.reservedQuantity -= amount;
        this.quantity += amount;
    }

    @PrePersist
    protected void onCreate() {
        if (this.stockId == null) {
            this.stockId = UuidUtils.createV7();
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.quantity == null) {
            this.quantity = 0;
        }
        this.active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
