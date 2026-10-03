package dev.flagwire.application.usecase;

import dev.flagwire.domain.event.DomainEvent;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentVersion;
import java.util.List;
import java.util.Map;

public record CommandResult<T>(
    T value, List<DomainEvent> events, Map<EnvironmentKey, EnvironmentVersion> versions) {

  public CommandResult {
    events = List.copyOf(events);
    versions = Map.copyOf(versions);
  }

  public static <T> CommandResult<T> unchanged(T value) {
    return new CommandResult<>(value, List.of(), Map.of());
  }

  public boolean changed() {
    return !this.events.isEmpty();
  }
}
