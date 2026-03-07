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

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    void assignOrder(OrderEntity order) {
        this.order = order;
    }

    @PrePersist
    protected void onCreate() {
        if (this.orderItemId == null) {
            this.orderItemId = UuidUtils.createV7();
        }
    }
}
