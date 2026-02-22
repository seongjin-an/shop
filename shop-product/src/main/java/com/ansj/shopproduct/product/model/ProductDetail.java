package com.ansj.shopproduct.product.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Data
public class ProductDetail {
    private Product product;
    private int quantity;

    public static ProductDetail of(Product product, int quantity) {
        return new ProductDetail(product, quantity);
    }
}
