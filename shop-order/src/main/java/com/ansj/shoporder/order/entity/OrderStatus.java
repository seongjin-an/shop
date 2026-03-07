package com.ansj.shoporder.order.entity;

/**
 * Saga 흐름에 따른 주문 상태 전이:
 *
 *                    ┌─────────────────┐
 *                    │     PENDING     │ ← 주문 생성, order-created 이벤트 발행
 *                    └────────┬────────┘
 *              stock-reserved │ stock-reserve-failed
 *              ┌──────────────┤
 *              ▼              ▼
 *   ┌────────────────┐  ┌─────────────┐
 *   │ STOCK_RESERVED │  │ STOCK_FAILED│ (terminal)
 *   └───────┬────────┘  └─────────────┘
 *           │ payment-success / payment-failed
 *    ┌──────┴───────┐
 *    ▼              ▼
 * ┌──────────┐  ┌────────────────┐
 * │COMPLETED │  │ PAYMENT_FAILED │ → order-cancelled 이벤트 발행 (보상)
 * │(terminal)│  └───────┬────────┘
 * └──────────┘          │ stock-reservation-cancelled
 *                       ▼
 *                 ┌──────────┐
 *                 │CANCELLED │ (terminal)
 *                 └──────────┘
 */
public enum OrderStatus {

    /** 주문 생성됨. order-created 이벤트 발행 후 stock-reserved 대기 중 */
    PENDING,

    /** 재고 예약 완료. 결제 요청 후 payment-success/failed 대기 중 */
    STOCK_RESERVED,

    /** 결제 성공, 주문 완료 */
    COMPLETED,

    /** 재고 부족으로 주문 실패 */
    STOCK_FAILED,

    /** 결제 실패. order-cancelled 이벤트로 재고 보상 트랜잭션 진행 중 */
    PAYMENT_FAILED,

    /** 보상 트랜잭션 완료 (재고 롤백됨) */
    CANCELLED
}
