package dev.flagwire.domain.event;

import java.time.ZonedDateTime;

public record Stamp(String eventId, ZonedDateTime occurredAt, String author) {}
