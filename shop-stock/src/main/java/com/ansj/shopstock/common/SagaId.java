package com.ansj.shopstock.common;

import java.util.Objects;
import java.util.UUID;

public record SagaId(UUID id) {
    public SagaId {
        Objects.requireNonNull(id, "SagaId cannot be null");
    }
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
