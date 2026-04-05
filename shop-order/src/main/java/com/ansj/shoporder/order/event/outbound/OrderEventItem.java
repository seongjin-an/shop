package com.ansj.shoporder.order.event.outbound;

import java.util.UUID;

/**
 * order-created 이벤트에 담기는 주문 항목.
 * shop-stock 이 이 정보를 바탕으로 재고를 예약한다.
 */
public record OrderEventItem(UUID productId, int quantity) {
}
