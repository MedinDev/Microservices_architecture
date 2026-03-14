package com.microservices.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DomainEvent(
    UUID eventId,
    EventType eventType,
    String aggregateType,
    String aggregateId,
    String sourceService,
    Instant occurredAt,
    String correlationId,
    Map<String, Object> payload
) {
}
