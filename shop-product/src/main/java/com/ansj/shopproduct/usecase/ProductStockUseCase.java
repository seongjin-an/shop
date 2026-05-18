package com.ansj.shopproduct.usecase;

import com.ansj.shopproduct.box.service.OutboxEventService;
import com.ansj.shopproduct.common.AggregateId;
import com.ansj.shopproduct.common.EventId;
import com.ansj.shopproduct.common.SagaId;
import com.ansj.shopproduct.product.dto.CreateProductDto;
import com.ansj.shopproduct.product.event.outbound.ProductCreatedEvent;
import com.ansj.shopproduct.product.event.outbound.StockItem;
import com.ansj.shopproduct.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ProductStockUseCase {

    @Value("${shop.kafka.topics.product-created.topic}")
    private String productCreatedTopic;

    private final ProductService productService;
    private final OutboxEventService outboxEventService;

    @Transactional
    public AggregateId createProductWithStock(CreateProductDto dto) {
        AggregateId productId = productService.createProduct(dto);
        SagaId sagaId = SagaId.newId();
        MDC.put("sagaId", sagaId.toString());
        try {
            StockItem stockItem = StockItem.of(productId, dto.getQuantity());
            ProductCreatedEvent event = new ProductCreatedEvent(
                    EventId.newId(),
                    sagaId,
                    productId,
                    "PRODUCT",
                    LocalDateTime.now(),
                    stockItem);

            // 상품 저장과 동일 트랜잭션 내에서 outbox INSERT → Debezium이 Kafka로 발행
            outboxEventService.save(event, productCreatedTopic, productId.toString());
            return productId;
        } finally {
            MDC.clear();
        }
    }
}
