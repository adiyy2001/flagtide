package dev.flagtide.domain.event;

import java.util.List;

public record Transition<T>(T next, List<DomainEvent> events) {

  public Transition {
    events = List.copyOf(events);
  }

  public static <T> Transition<T> of(T next, DomainEvent event) {
    return new Transition<>(next, List.of(event));
  }

  public static <T> Transition<T> unchanged(T current) {
    return new Transition<>(current, List.of());
  }

  public boolean changed() {
    return !this.events.isEmpty();
  }
}
