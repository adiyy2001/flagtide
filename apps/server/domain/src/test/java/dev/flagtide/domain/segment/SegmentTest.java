package dev.flagtide.domain.segment;

import static dev.flagtide.domain.flag.FlagFixtures.DEV;
import static dev.flagtide.domain.flag.FlagFixtures.NOW;
import static dev.flagtide.domain.flag.FlagFixtures.stamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.domain.evaluation.AttributeValue;
import dev.flagtide.domain.evaluation.Condition;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.evaluation.Operator;
import dev.flagtide.domain.event.DomainEvent;
import dev.flagtide.domain.event.Transition;
import dev.flagtide.domain.json.Json;
import dev.flagtide.domain.value.Revision;
import dev.flagtide.domain.value.SegmentKey;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SegmentTest {

  private static final SegmentKey BETA = new SegmentKey("beta");
  private static final Condition.Attribute POLAND =
      new Condition.Attribute(
          "country", Operator.EQUALS, List.of(new AttributeValue.TextValue("PL")), false);

  private static Segment segment() {
    return Segment.create(
            DEV,
            BETA,
            "Beta testers",
            Set.of("u1"),
            Set.of("u9"),
            List.of(List.of(POLAND)),
            stamp("ann"))
        .next();
  }

  @Test
  void createsASegmentAndEmitsOneEvent() {
    Transition<Segment> created =
        Segment.create(DEV, BETA, "Beta testers", Set.of("u1"), Set.of(), List.of(), stamp("ann"));

    assertThat(created.next().revision()).isEqualTo(Revision.FIRST);
    assertThat(created.next().createdAt()).isEqualTo(NOW);
    assertThat(created.events())
        .containsExactly(new DomainEvent.SegmentSaved(stamp("ann"), DEV, BETA, true));
  }

  @Test
  void updatesASegmentAndBumpsTheRevision() {
    Segment segment = segment();

    Transition<Segment> updated =
        segment.update("Renamed", Set.of("u1", "u2"), Set.of(), List.of(), stamp("bob"));

    assertThat(updated.next().name()).isEqualTo("Renamed");
    assertThat(updated.next().included()).containsExactlyInAnyOrder("u1", "u2");
    assertThat(updated.next().revision()).isEqualTo(Revision.of(2));
    assertThat(updated.events())
        .containsExactly(new DomainEvent.SegmentSaved(stamp("bob"), DEV, BETA, false));
  }

  @Test
  void emitsNothingForAnUnchangedUpdate() {
    Segment segment = segment();

    Transition<Segment> updated =
        segment.update(
            segment.name(), segment.included(), segment.excluded(), segment.rules(), stamp("a"));

    assertThat(updated.changed()).isFalse();
    assertThat(updated.next()).isSameAs(segment);
  }

  @Test
  void deletingEmitsOneEvent() {
    assertThat(segment().delete(stamp("ann")))
        .isEqualTo(new DomainEvent.SegmentDeleted(stamp("ann"), DEV, BETA));
  }

  @Test
  void rejectsAKeyThatIsBothIncludedAndExcluded() {
    assertThatThrownBy(
            () -> Segment.create(DEV, BETA, "n", Set.of("u1"), Set.of("u1"), List.of(), stamp("a")))
        .hasMessageContaining("both included and excluded");
  }

  @Test
  void rejectsBlankOrLongNames() {
    assertThatThrownBy(
            () -> Segment.create(DEV, BETA, " ", Set.of(), Set.of(), List.of(), stamp("a")))
        .hasMessageContaining("name");
    assertThatThrownBy(
            () ->
                Segment.create(
                    DEV, BETA, "x".repeat(101), Set.of(), Set.of(), List.of(), stamp("a")))
        .hasMessageContaining("name");
  }

  @Test
  void rejectsInvalidConditionsInRuleGroups() {
    Condition.Attribute broken =
        new Condition.Attribute("country", Operator.EQUALS, List.of(), false);

    assertThatThrownBy(
            () ->
                Segment.create(
                    DEV, BETA, "n", Set.of(), Set.of(), List.of(List.of(broken)), stamp("a")))
        .hasMessageContaining("values");
  }

  @Test
  void limitsTheSizeOfASegment() {
    Set<String> tooMany =
        IntStream.range(0, 10_001).mapToObj(index -> "u" + index).collect(Collectors.toSet());
    List<List<Condition.Attribute>> tooManyGroups =
        IntStream.range(0, 51).mapToObj(index -> List.of(POLAND)).toList();

    assertThatThrownBy(
            () -> Segment.create(DEV, BETA, "n", tooMany, Set.of(), List.of(), stamp("a")))
        .hasMessageContaining("at most 10000");
    assertThatThrownBy(
            () -> Segment.create(DEV, BETA, "n", Set.of(), Set.of(), tooManyGroups, stamp("a")))
        .hasMessageContaining("at most 50");
  }

  @Test
  void roundTripsThroughItsDocument() {
    Segment segment = segment();

    assertThat(SegmentDocument.fromJson(SegmentDocument.toJson(segment))).isEqualTo(segment);
  }

  @Test
  void rejectsDocumentsWithBadMembers() {
    JsonValue bad =
        Json.object()
            .text("environment", "dev")
            .text("key", "beta")
            .text("name", "n")
            .number("revision", 1)
            .text("createdAt", NOW.toString())
            .text("updatedAt", NOW.toString())
            .put("included", new JsonValue.JsonArray(List.of(Json.number(1))))
            .put("excluded", new JsonValue.JsonArray(List.of()))
            .put("rules", new JsonValue.JsonArray(List.of()))
            .build();

    assertThatThrownBy(() -> SegmentDocument.fromJson(bad))
        .hasMessageContaining("must be a string");
  }
}
