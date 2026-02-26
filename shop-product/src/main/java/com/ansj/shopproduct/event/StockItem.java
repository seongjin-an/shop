package com.ansj.shopproduct.event;

import com.ansj.shopproduct.common.AggregateId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
@Data
public class StockItem {
    private AggregateId productId;
    private int quantity;
}
