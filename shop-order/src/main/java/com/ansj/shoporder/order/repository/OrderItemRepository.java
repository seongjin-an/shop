package com.ansj.shoporder.order.repository;

import com.ansj.shoporder.order.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Aggregator 가 per-item 결과 이벤트를 처리할 때 사용.
 */
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {
    /**
     * Kafka 에서 수신한 orderItemId 로 아이템을 조회한다.
     * 낙관적 락 이슈를 피하려고 {@code @Version} 은 걸지 않고,
     * per-productId 파티션 직렬화 + DB row 단위 update 로 처리한다.
     */
    Optional<OrderItemEntity> findByOrderItemId(UUID orderItemId);
}
