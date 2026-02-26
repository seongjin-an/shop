package com.ansj.shopproduct.product.dto;

import com.ansj.shopproduct.product.entity.ProductEntity;
import com.ansj.shopproduct.product.entity.ProductStatus;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CreateProductDto {
    @NotEmpty
    private String productName;
    @NotEmpty
    private String productDesc;
    @NotEmpty
    private BigDecimal productPrice;

    @NotEmpty
    private Integer quantity;

    public ProductEntity toEntity() {
        return ProductEntity.builder()
                .productName(productName)
                .productDesc(productDesc)
                .productPrice(productPrice)
                .productStatus(ProductStatus.ACTIVE)
                .build();
    }
}
