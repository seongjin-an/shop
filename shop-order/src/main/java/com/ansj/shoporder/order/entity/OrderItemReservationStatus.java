package com.ansj.shoporder.order.entity;

/**
 * 단일 OrderItem 의 재고 라이프사이클 상태.
 *
 * <p>주문 전체(OrderStatus) 와 별개로, 각 아이템이 어디까지 진행되었는지 추적하기 위한 상태값.
 * Aggregator 가 같은 sagaId 에 속한 모든 아이템이 {@link #RESERVED} 에 도달해야만
 * payment-requested 를 발행하도록 제어한다.
 *
 * <pre>
 *   PENDING → RESERVED → CONFIRMED  (정상)
 *   PENDING → FAILED                (재고 부족)
 *   RESERVED → RELEASED             (결제 실패 보상)
 * </pre>
 */
public enum OrderItemReservationStatus {

    /** stock-reservation-requested 발행 후 응답 대기 */
    PENDING,

    /** stock-reserved 수신 완료 */
    RESERVED,

    /** stock-reserve-failed 수신 (터미널) */
    FAILED,

    /** 결제 성공 후 stock-confirm-requested 발행 완료 (터미널) */
    CONFIRMED,

    /** 보상으로 stock-release-requested 발행 완료 (터미널) */
    RELEASED
}
