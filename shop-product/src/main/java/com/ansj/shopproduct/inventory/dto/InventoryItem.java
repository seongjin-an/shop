package com.ansj.shopproduct.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class InventoryItem {
    private UUID productId;
    private int quantity;
}
