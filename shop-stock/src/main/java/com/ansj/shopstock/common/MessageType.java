package com.ansj.shopstock.common;

public class MessageType {
    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";

    public static final String ORDER_CREATED = "ORDER_CREATED";

    // ─── per-item stock lifecycle (수신: key=productId) ───
    public static final String STOCK_RESERVATION_REQUESTED = "STOCK_RESERVATION_REQUESTED";
    public static final String STOCK_CONFIRM_REQUESTED     = "STOCK_CONFIRM_REQUESTED";
    public static final String STOCK_RELEASE_REQUESTED     = "STOCK_RELEASE_REQUESTED";

    // ─── per-item stock results (발행: key=productId) ───
    public static final String STOCK_RESERVED       = "STOCK_RESERVED";
    public static final String STOCK_RESERVE_FAILED = "STOCK_RESERVE_FAILED";

    public static final String PAYMENT_SUCCESS = "PAYMENT_SUCCESS";

    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
}
