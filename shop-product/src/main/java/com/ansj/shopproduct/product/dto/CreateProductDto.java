package com.ansj.shopproduct.product.dto;

import com.ansj.shopproduct.product.entity.ProductEntity;
import com.ansj.shopproduct.product.entity.ProductStatus;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProductDto {
    @NotEmpty
    private String productName;
    @NotEmpty
    private String productDesc;
    @NotEmpty
    private BigDecimal productPrice;

    public ProductEntity toEntity() {
        return ProductEntity.builder()
                .productName(productName)
                .productDesc(productDesc)
                .productPrice(productPrice)
                .productStatus(ProductStatus.ACTIVE)
                .build();
    }
}
