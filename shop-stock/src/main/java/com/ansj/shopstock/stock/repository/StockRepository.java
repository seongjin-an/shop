package com.ansj.shopstock.stock.repository;

import com.ansj.shopstock.stock.entity.StockEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<StockEntity, UUID> {
    // 비관적 배타 락 (Pessimistic Write Lock) 적용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockEntity s where s.productId = :productId")
    Optional<StockEntity> findByProductId(UUID productId);

    boolean existsByProductId(UUID productId);

    List<StockEntity> findByProductIdIn(Collection<UUID> productIds);
}
