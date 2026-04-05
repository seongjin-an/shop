package com.ansj.shoppayment.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.UUID;

public record SagaId(@JsonValue UUID id) {
    public SagaId {
        Objects.requireNonNull(id, "SagaId cannot be null");
    }

    public static SagaId newId() {
        return new SagaId(UuidUtils.createV7());
    }

    public static SagaId from(UUID value) {
        return new SagaId(value);
    }

    @JsonCreator
    public static SagaId from(String value) {
        return new SagaId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
