package com.ansj.shopproduct.common;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

public record EventId(@JsonValue UUID id) {
    public static EventId newId() {
        return new EventId(UuidUtils.createV7());
    }

    public static EventId from(String value) {
        return new EventId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
