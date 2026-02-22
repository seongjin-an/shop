package com.ansj.shopproduct.product.entity;

import com.ansj.shopproduct.common.UuidUtils;
import com.ansj.shopproduct.product.model.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
        name = "product",
        indexes = {
                @Index(name = "idx_product_status_created", columnList = "product_status, created_at DESC")
        }
)
@Entity
public class ProductEntity {

    @Id
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_desc", columnDefinition = "TEXT")
    private String productDesc;

    @Column(name = "product_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal productPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_status", nullable = false)
    private ProductStatus productStatus;

    /**
     * 소프트 삭제
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Product toModel() {
        return Product.of(productId, productName, productDesc, productPrice, productStatus);
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
    }

    public void changePrice(BigDecimal productPrice) {
        validatePrice(productPrice);
        this.productPrice = productPrice;
    }
    public void changeName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("상품명은 비어 있을 수 없습니다.");
        }
        this.productName = newName;
    }

    public void activate() {
        this.productStatus = ProductStatus.ACTIVE;
    }

    public void deactivate() {
        this.productStatus = ProductStatus.INACTIVE;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.productStatus = ProductStatus.DELETED;
    }

    @PrePersist
    protected void onCreate() {
        if (this.productId == null) {
            this.productId = UuidUtils.createV7();
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;

        if (this.productStatus == null) {
            this.productStatus = ProductStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
