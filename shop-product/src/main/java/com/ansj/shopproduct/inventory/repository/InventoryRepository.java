package com.ansj.shopproduct.inventory.repository;

import com.ansj.shopproduct.inventory.entity.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryEntity, Long> {
    Optional<InventoryEntity> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    List<InventoryEntity> findByProductIdIn(Collection<Long> productIds);
}
