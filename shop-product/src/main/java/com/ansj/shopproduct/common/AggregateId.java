package com.ansj.shopproduct.common;

import java.util.UUID;

public record AggregateId(UUID id) {
    public static AggregateId newId() {
        return new AggregateId(UuidUtils.createV7());
    }

    public static AggregateId from(String value) {
        return new AggregateId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
