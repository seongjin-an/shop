package com.ansj.shopproduct.inventory.repository;

import com.ansj.shopproduct.inventory.entity.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<InventoryEntity, UUID> {
    Optional<InventoryEntity> findByProductId(UUID productId);

    boolean existsByProductId(UUID productId);

    List<InventoryEntity> findByProductIdIn(Collection<UUID> productIds);
}
