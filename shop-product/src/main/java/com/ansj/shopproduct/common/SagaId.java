package com.ansj.shopproduct.common;

import java.util.UUID;

public record SagaId(UUID id) {
    public static SagaId newId() {
        return new SagaId(UuidUtils.createV7());
    }

    public static SagaId from(String value) {
        return new SagaId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
