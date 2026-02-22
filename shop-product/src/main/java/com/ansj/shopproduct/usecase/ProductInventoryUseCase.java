package com.ansj.shopproduct.usecase;

import com.ansj.shopproduct.common.AggregateId;
import com.ansj.shopproduct.event.service.InboxEventService;
import com.ansj.shopproduct.event.service.OutboxEventService;
import com.ansj.shopproduct.inventory.dto.inbound.OrderCreatedEvent;
import com.ansj.shopproduct.product.model.ProductDetail;
import com.ansj.shopproduct.inventory.service.InventoryService;
import com.ansj.shopproduct.product.dto.CreateProductDto;
import com.ansj.shopproduct.product.model.Product;
import com.ansj.shopproduct.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ProductInventoryUseCase {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final InboxEventService inboxEventService;
    private final OutboxEventService outboxEventService;

    private final KafkaTemplate<String, String> kafkaTemplate;

    /* 나중에 read service, write service 를 분리하든가 */

    @Transactional
    public AggregateId createProductWithInventory(CreateProductDto dto, int initialQuantity) {
        AggregateId productId = productService.createProduct(dto);

        inventoryService.initializeProductInventory(productId, initialQuantity);

        return productId;
    }

    public ProductDetail getProductDetail(UUID productId) {
        Product product = productService.getProduct(productId);
        int quantity = inventoryService.getQuantity(productId);
        return ProductDetail.of(product, quantity);
    }

    /* order 서비스로부터 발행된 이벤트로 인해 invoke ! */
    @Transactional
    public void order(UUID productId, int quantity) {
        if (!productService.isOrderable(productId)) {
            throw new IllegalStateException("주문 불가 상품");
        }
        inventoryService.decreaseInventory(productId, quantity);
    }

    @Transactional
    public void processOrder(OrderCreatedEvent orderCreatedEvent) {
        if (inboxEventService.existsByEventId(orderCreatedEvent.getEventId())) {
            return;
        }

        inventoryService.reserve(orderCreatedEvent.getItems());

        inboxEventService.createInboxEvent(orderCreatedEvent);

        outboxEventService.inventoryReservedEvent(orderCreatedEvent.getSagaId(), orderCreatedEvent.getAggregateId());
    }
}
