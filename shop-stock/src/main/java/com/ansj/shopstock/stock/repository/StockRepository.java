package com.ansj.shopstock.stock.repository;

import com.ansj.shopstock.stock.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<StockEntity, UUID> {
    Optional<StockEntity> findByProductId(UUID productId);

    boolean existsByProductId(UUID productId);

    List<StockEntity> findByProductIdIn(Collection<UUID> productIds);
}
