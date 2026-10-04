package dev.flagtide.domain.event;

import java.time.ZonedDateTime;

public record Stamp(String eventId, ZonedDateTime occurredAt, String author) {}
