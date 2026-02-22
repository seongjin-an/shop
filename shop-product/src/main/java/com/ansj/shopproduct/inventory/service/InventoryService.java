package com.ansj.shopproduct.inventory.service;

import com.ansj.shopproduct.inventory.dto.InventoryItem;
import com.ansj.shopproduct.inventory.entity.InventoryEntity;
import com.ansj.shopproduct.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public int getQuantity(Long productId) {
        return getInventory(productId).getQuantity();
    }

    public boolean exists(Long productId) {
        return inventoryRepository.existsByProductId(productId);
    }
    public boolean canDecrease(Long productId, int amount) {
        InventoryEntity inventory = getInventory(productId);
        return inventory.getQuantity() >= amount;
    }

    @Transactional
    public void initializeInventory(Long productId, int quantity) {
        InventoryEntity inventory = InventoryEntity.builder()
                .productId(productId)
                .quantity(quantity)
                .active(true)
                .build();

        inventoryRepository.save(inventory);
    }

    @Transactional
    public void increaseInventory(Long productId, int amount) {
        InventoryEntity inventory = getInventory(productId);
        inventory.increase(amount);
    }

    @Transactional
    public void decreaseInventory(Long productId, int amount) {
        InventoryEntity inventory = getInventory(productId);
        if (inventory.getQuantity() < amount) throw new IllegalStateException("재고가 존재하지 않습니다.");

        inventory.decrease(amount);
    }

    private InventoryEntity getInventory(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("재고가 존재하지 않습니다."));
    }

    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class}, // 낙관적 락 예외 발생 시
            maxAttempts = 3, // 최대 3번 시도
            backoff = @Backoff(delay = 100, multiplier = 2) // 100ms 대기 후, 실패마다 2배씩 대기시간 증가
    )
    @Transactional
    public void reserve(List<InventoryItem> items) {
        Map<Long, Integer> itemMap = items.stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, InventoryItem::getQuantity));

        List<InventoryEntity> inventories = inventoryRepository.findByProductIdIn(itemMap.keySet());
        for (InventoryEntity inventoryEntity : inventories) {
            inventoryEntity.reserve(itemMap.get(inventoryEntity.getProductId()));
        }
    }
}
