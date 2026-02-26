package com.ansj.shopstock.stock.dto;

import com.ansj.shopstock.common.AggregateId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class StockItem {
    private AggregateId productId;
    private int quantity;
}
