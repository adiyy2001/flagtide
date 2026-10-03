package dev.flagwire.domain.segment;

import dev.flagwire.domain.condition.ConditionRules;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.Condition;
import dev.flagwire.domain.event.DomainEvent;
import dev.flagwire.domain.event.Stamp;
import dev.flagwire.domain.event.Transition;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.Revision;
import dev.flagwire.domain.value.SegmentKey;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record Segment(
    EnvironmentKey environment,
    SegmentKey key,
    String name,
    Set<String> included,
    Set<String> excluded,
    List<List<Condition.Attribute>> rules,
    Revision revision,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt) {

  private static final int MAX_MEMBERS = 10_000;
  private static final int MAX_GROUPS = 50;
  private static final int MAX_NAME = 100;

  public Segment {
    included = Set.copyOf(included);
    excluded = Set.copyOf(excluded);
    rules = rules.stream().map(List::copyOf).toList();
    requireName(name);
    requireMembers(included, excluded);
    requireRules(rules);
    if (revision.compareTo(Revision.FIRST) < 0) {
      throw FlagwireException.invalid("revision", "a stored segment has a revision of at least 1");
    }
  }

  public static Transition<Segment> create(
      EnvironmentKey environment,
      SegmentKey key,
      String name,
      Set<String> included,
      Set<String> excluded,
      List<List<Condition.Attribute>> rules,
      Stamp stamp) {
    Segment created =
        new Segment(
            environment,
            key,
            name,
            included,
            excluded,
            rules,
            Revision.FIRST,
            stamp.occurredAt(),
            stamp.occurredAt());
    return Transition.of(created, new DomainEvent.SegmentSaved(stamp, environment, key, true));
  }

  public Transition<Segment> update(
      String newName,
      Set<String> newIncluded,
      Set<String> newExcluded,
      List<List<Condition.Attribute>> newRules,
      Stamp stamp) {
    Segment candidate =
        new Segment(
            this.environment,
            this.key,
            newName,
            newIncluded,
            newExcluded,
            newRules,
            this.revision.next(),
            this.createdAt,
            stamp.occurredAt());
    boolean unchanged =
        this.name.equals(candidate.name)
            && this.included.equals(candidate.included)
            && this.excluded.equals(candidate.excluded)
            && this.rules.equals(candidate.rules);
    return unchanged
        ? Transition.unchanged(this)
        : Transition.of(
            candidate, new DomainEvent.SegmentSaved(stamp, this.environment, this.key, false));
  }

  public DomainEvent delete(Stamp stamp) {
    return new DomainEvent.SegmentDeleted(stamp, this.environment, this.key);
  }

  private static void requireName(String name) {
    if (name.isBlank() || name.length() > MAX_NAME) {
      throw FlagwireException.invalid("name", "must be 1 to 100 characters and not blank");
    }
  }

  private static void requireMembers(Set<String> included, Set<String> excluded) {
    if (included.size() > MAX_MEMBERS || excluded.size() > MAX_MEMBERS) {
      throw FlagwireException.invalid("members", "a segment lists at most 10000 keys per side");
    }
    Set<String> overlap = new HashSet<>(included);
    overlap.retainAll(excluded);
    if (!overlap.isEmpty()) {
      throw FlagwireException.invalid("members", "a key cannot be both included and excluded");
    }
  }

  private static void requireRules(List<List<Condition.Attribute>> rules) {
    if (rules.size() > MAX_GROUPS) {
      throw FlagwireException.invalid("rules", "a segment has at most 50 rule groups");
    }
    rules.forEach(group -> group.forEach(ConditionRules::validate));
  }
}
