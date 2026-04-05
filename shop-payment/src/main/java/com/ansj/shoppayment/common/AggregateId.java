package com.ansj.shoppayment.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.UUID;

public record AggregateId(@JsonValue UUID id) {
    public AggregateId {
        Objects.requireNonNull(id, "AggregateId cannot be null");
    }

    public static AggregateId newId() {
        return new AggregateId(UuidUtils.createV7());
    }

    public static AggregateId from(UUID value) {
        return new AggregateId(value);
    }

    @JsonCreator
    public static AggregateId from(String value) {
        return new AggregateId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
