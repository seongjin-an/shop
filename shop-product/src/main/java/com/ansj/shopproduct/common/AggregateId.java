package com.ansj.shopproduct.common;

import java.util.Objects;
import java.util.UUID;

public record AggregateId(UUID id) {
    public AggregateId {
        Objects.requireNonNull(id, "AggregateId cannot be null");
    }

    public static AggregateId newId() {
        return new AggregateId(UuidUtils.createV7());
    }

    public static AggregateId from(String value) {
        return new AggregateId(UUID.fromString(value));
    }

    public static AggregateId from(UUID value) {
        return new AggregateId(value);
    }
}
