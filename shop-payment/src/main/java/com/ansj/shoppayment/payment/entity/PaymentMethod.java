package com.ansj.shoppayment.payment.entity;

public enum PaymentMethod {
    CARD,
    BANK_TRANSFER, // 계좌 이체
    VIRTUAL_ACCOUNT, // 가상 계좌 입급
    MOBILE,
    KAKAO_PAY,
    NAVER_PAY
    ;
}
