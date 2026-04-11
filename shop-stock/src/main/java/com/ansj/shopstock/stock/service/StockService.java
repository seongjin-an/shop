package com.ansj.shopstock.stock.service;

import com.ansj.shopstock.common.AggregateId;
import com.ansj.shopstock.stock.dto.StockItem;
import com.ansj.shopstock.stock.entity.StockEntity;
import com.ansj.shopstock.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class StockService {

    private final StockRepository stockRepository;

    public int getQuantity(UUID productId) {
        return getStock(productId).getQuantity();
    }

    public boolean exists(UUID productId) {
        return stockRepository.existsByProductId(productId);
    }
    public boolean canDecrease(UUID productId, int amount) {
        StockEntity stock = getStock(productId);
        return stock.getQuantity() >= amount;
    }

    @Transactional
    public void createStock(UUID productId, int quantity) {
        StockEntity stock = StockEntity.builder()
                .productId(productId)
                .quantity(quantity)
                .reservedQuantity(0)
                .active(true)
                .build();

        stockRepository.save(stock);
    }

    @Transactional
    public void increaseStock(UUID productId, int amount) {
        StockEntity stock = getStock(productId);
        stock.increase(amount);
    }

    @Transactional
    public void decreaseStock(UUID productId, int amount) {
        StockEntity stock = getStock(productId);
        if (stock.getQuantity() < amount) throw new IllegalStateException("재고가 존재하지 않습니다.");

        stock.decrease(amount);
    }

    private StockEntity getStock(UUID productId) {
        return stockRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("재고가 존재하지 않습니다."));
    }

    @Retryable(retryFor = {ObjectOptimisticLockingFailureException.class}, maxAttempts = 5, backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 500))
    @Transactional
    public void confirmReservations(List<StockItem> items) {
        Map<UUID, Integer> itemMap = items.stream()
                .collect(Collectors.toMap(item -> item.getProductId().id(), StockItem::getQuantity));

        List<StockEntity> inventories = stockRepository.findByProductIdIn(itemMap.keySet());
        for (StockEntity stock : inventories) {
            stock.confirmReservation(itemMap.get(stock.getProductId()));
        }
    }

    @Retryable(retryFor = {ObjectOptimisticLockingFailureException.class}, maxAttempts = 5, backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 500))
    @Transactional
    public void cancelReservations(List<StockItem> items) {
        Map<UUID, Integer> itemMap = items.stream()
                .collect(Collectors.toMap(item -> item.getProductId().id(), StockItem::getQuantity));

        List<StockEntity> inventories = stockRepository.findByProductIdIn(itemMap.keySet());
        for (StockEntity stock : inventories) {
            stock.cancelReservation(itemMap.get(stock.getProductId()));
        }
    }

    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class}, // 낙관적 락 예외 발생 시
            maxAttempts = 3, // 최대 3번 시도
            backoff = @Backoff(delay = 100, multiplier = 2) // 100ms 대기 후, 실패마다 2배씩 대기시간 증가
    )
    @Transactional
    public void reserve(List<StockItem> items) {
        Map<UUID, Integer> itemMap = items.stream()
                .collect(Collectors.toMap(item -> item.getProductId().id(), StockItem::getQuantity));

        List<StockEntity> inventories = stockRepository.findByProductIdIn(itemMap.keySet());

        if (inventories.size() != itemMap.size()) {
            throw new IllegalStateException("재고 정보가 없는 상품이 포함되어 있습니다.");
        }

        for (StockEntity stockEntity : inventories) {
            stockEntity.reserve(itemMap.get(stockEntity.getProductId()));
        }
    }
}
