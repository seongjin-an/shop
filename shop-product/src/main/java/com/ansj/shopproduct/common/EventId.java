package com.ansj.shopproduct.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.UUID;

public record EventId(@JsonValue UUID id) {
    public EventId {
        Objects.requireNonNull(id, "EventId cannot be null");
    }

    public static EventId newId() {
        return new EventId(UuidUtils.createV7());
    }

    @JsonCreator
    public static EventId from(String value) {
        return new EventId(UUID.fromString(value));
    }

    public static EventId from(UUID value) {
        return new EventId(value);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
