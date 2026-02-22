package com.ansj.shopproduct.product.model;

import com.ansj.shopproduct.product.entity.ProductStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Data
public class Product {
    private final Long productId;
    private final String productName;
    private final String productDesc;
    private final BigDecimal productPrice;
    private final ProductStatus productStatus;

    public static Product of(Long productId, String productName, String productDesc, BigDecimal productPrice, ProductStatus productStatus) {
        return new  Product(productId, productName, productDesc, productPrice, productStatus);
    }
}
