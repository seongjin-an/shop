package com.ansj.shopproduct.usecase;

import com.ansj.shopproduct.box.service.InboxEventService;
import com.ansj.shopproduct.box.service.OutboxEventService;
import com.ansj.shopproduct.common.AggregateId;
import com.ansj.shopproduct.common.EventId;
import com.ansj.shopproduct.common.JsonUtil;
import com.ansj.shopproduct.common.SagaId;
import com.ansj.shopproduct.event.ProductCreatedEvent;
import com.ansj.shopproduct.event.StockItem;
import com.ansj.shopproduct.product.dto.CreateProductDto;
import com.ansj.shopproduct.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
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
    //private final InboxEventService inboxEventService;
    //private final OutboxEventService outboxEventService;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonUtil jsonUtil;

    /* 나중에 read service, write service 를 분리하든가 */

    @Transactional
    public AggregateId createProductWithStock(CreateProductDto dto) {
        AggregateId productId = productService.createProduct(dto);

        StockItem stockItem = StockItem.of(productId, dto.getQuantity());

        // outbox event 는 나중에 적용
        ProductCreatedEvent product = new ProductCreatedEvent(
                EventId.newId(),
                SagaId.newId(),
                productId,
                "PRODUCT",
                LocalDateTime.now(),
                stockItem);

        jsonUtil.toJson(product)
                        .ifPresent(json -> kafkaTemplate.send(productCreatedTopic, json));

        return productId;
    }
}
