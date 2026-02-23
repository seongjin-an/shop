package com.ansj.shopproduct.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class StockItem {
    private UUID productId;
    private int quantity;
}
