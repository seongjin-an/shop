package com.ansj.shoporder.common;

public class MessageType {
    public static final String ORDER_CREATED  = "ORDER_CREATED";

    // ─── per-item stock lifecycle (shop-order → shop-stock, key=productId) ───
    public static final String STOCK_RESERVATION_REQUESTED = "STOCK_RESERVATION_REQUESTED";
    public static final String STOCK_CONFIRM_REQUESTED     = "STOCK_CONFIRM_REQUESTED";
    public static final String STOCK_RELEASE_REQUESTED     = "STOCK_RELEASE_REQUESTED";

    // ─── per-item stock results (shop-stock → shop-order, key=productId) ───
    public static final String STOCK_RESERVED        = "STOCK_RESERVED";
    public static final String STOCK_RESERVE_FAILED  = "STOCK_RESERVE_FAILED";

    // ─── payment lifecycle (key=sagaId; payment 는 상품 단위 contention 없음) ───
    public static final String PAYMENT_REQUESTED = "PAYMENT_REQUESTED";
    public static final String PAYMENT_SUCCESS   = "PAYMENT_SUCCESS";
    public static final String PAYMENT_FAILED    = "PAYMENT_FAILED";

    public static final String ORDER_CANCELLED   = "ORDER_CANCELLED";
}
