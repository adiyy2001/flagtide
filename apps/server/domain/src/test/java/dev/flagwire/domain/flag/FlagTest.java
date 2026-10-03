package dev.flagwire.domain.flag;

import static dev.flagwire.domain.flag.FlagFixtures.DEV;
import static dev.flagwire.domain.flag.FlagFixtures.NOW;
import static dev.flagwire.domain.flag.FlagFixtures.OFF;
import static dev.flagwire.domain.flag.FlagFixtures.ON;
import static dev.flagwire.domain.flag.FlagFixtures.PROD;
import static dev.flagwire.domain.flag.FlagFixtures.STAGING;
import static dev.flagwire.domain.flag.FlagFixtures.booleanFlag;
import static dev.flagwire.domain.flag.FlagFixtures.booleanVariants;
import static dev.flagwire.domain.flag.FlagFixtures.settings;
import static dev.flagwire.domain.flag.FlagFixtures.stamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.domain.error.FlagwireError;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.AttributeValue;
import dev.flagwire.domain.evaluation.Condition;
import dev.flagwire.domain.evaluation.FlagType;
import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.evaluation.Operator;
import dev.flagwire.domain.event.DomainEvent;
import dev.flagwire.domain.event.Transition;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.Percentage;
import dev.flagwire.domain.value.Revision;
import dev.flagwire.domain.value.RuleId;
import dev.flagwire.domain.value.Salt;
import dev.flagwire.domain.value.SegmentKey;
import dev.flagwire.domain.value.VariantKey;
import dev.flagwire.domain.value.Weight;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FlagTest {

  private static TargetingRule rule(
      String id, int order, Serving serving, Condition... conditions) {
    return new TargetingRule(RuleId.of(id), order, List.of(conditions), serving);
  }

  private static Condition.Attribute plan(String value) {
    return new Condition.Attribute(
        "plan", Operator.EQUALS, List.of(new AttributeValue.TextValue(value)), false);
  }

  @Test
  void createsAFlagThatIsOffInEveryEnvironmentAndEmitsOneEvent() {
    Transition<Flag> created =
        FlagFixtures.create("new-checkout", FlagType.BOOLEAN, booleanVariants(), OFF, OFF);

    Flag flag = created.next();
    assertThat(flag.revision()).isEqualTo(Revision.FIRST);
    assertThat(flag.environments().keySet()).containsExactlyInAnyOrder(DEV, STAGING, PROD);
    assertThat(flag.environments().values())
        .allSatisfy(
            config -> {
              assertThat(config.enabled()).isFalse();
              assertThat(config.killSwitch()).isFalse();
              assertThat(config.offVariant()).isEqualTo(OFF);
              assertThat(config.rules()).isEmpty();
            });
    assertThat(created.events())
        .containsExactly(
            new DomainEvent.FlagCreated(
                stamp("seed"), new FlagKey("new-checkout"), Set.of(DEV, STAGING, PROD)));
  }

  @Test
  void rejectsAnEmptyListOfVariants() {
    assertThatThrownBy(() -> FlagFixtures.create("f", FlagType.BOOLEAN, List.of(), OFF, OFF))
        .isInstanceOf(FlagwireException.class);
  }

  @Test
  void rejectsDuplicateVariantKeys() {
    List<FlagVariant> twice =
        List.of(
            new FlagVariant(ON, new JsonValue.JsonBoolean(true)),
            new FlagVariant(ON, new JsonValue.JsonBoolean(false)));

    assertThatThrownBy(() -> FlagFixtures.create("f", FlagType.BOOLEAN, twice, ON, ON))
        .hasMessageContaining("used twice");
  }

  @Test
  void rejectsABooleanFlagWithTwoVariantsOfTheSameValue() {
    List<FlagVariant> same =
        List.of(
            new FlagVariant(ON, new JsonValue.JsonBoolean(true)),
            new FlagVariant(OFF, new JsonValue.JsonBoolean(true)));

    assertThatThrownBy(() -> FlagFixtures.create("f", FlagType.BOOLEAN, same, ON, ON))
        .hasMessageContaining("one variant per value");
  }

  @Test
  void rejectsMoreThanSixtyFourVariants() {
    List<FlagVariant> many =
        IntStream.range(0, 65)
            .mapToObj(
                index ->
                    new FlagVariant(
                        new VariantKey("v" + index), new JsonValue.JsonString("v" + index)))
            .toList();

    assertThatThrownBy(
            () ->
                FlagFixtures.create(
                    "f", FlagType.STRING, many, new VariantKey("v0"), new VariantKey("v0")))
        .hasMessageContaining("1 to 64 variants");
  }

  @Test
  void acceptsVariantValuesThatMatchEachFlagType() {
    VariantKey a = new VariantKey("a");
    assertThat(
            FlagFixtures.create(
                "s",
                FlagType.STRING,
                List.of(new FlagVariant(a, new JsonValue.JsonString("x"))),
                a,
                a))
        .isNotNull();
    assertThat(
            FlagFixtures.create(
                "n",
                FlagType.NUMBER,
                List.of(new FlagVariant(a, new JsonValue.JsonNumber(1.5))),
                a,
                a))
        .isNotNull();
    assertThat(
            FlagFixtures.create(
                "j",
                FlagType.JSON,
                List.of(new FlagVariant(a, new JsonValue.JsonObject(Map.of()))),
                a,
                a))
        .isNotNull();
    assertThat(
            FlagFixtures.create(
                "k",
                FlagType.JSON,
                List.of(new FlagVariant(a, new JsonValue.JsonArray(List.of()))),
                a,
                a))
        .isNotNull();
  }

  @Test
  void rejectsVariantValuesThatDoNotMatchTheFlagType() {
    VariantKey a = new VariantKey("a");
    Map<FlagType, JsonValue> wrongValues =
        Map.of(
            FlagType.BOOLEAN, new JsonValue.JsonString("true"),
            FlagType.STRING, new JsonValue.JsonNumber(1),
            FlagType.NUMBER, new JsonValue.JsonString("1"),
            FlagType.JSON, new JsonValue.JsonString("{}"));

    wrongValues.forEach(
        (type, value) ->
            assertThatThrownBy(
                    () -> FlagFixtures.create("f", type, List.of(new FlagVariant(a, value)), a, a))
                .hasMessageContaining("does not match the flag type"));
  }

  @Test
  void rejectsNonFiniteNumberVariants() {
    VariantKey a = new VariantKey("a");

    assertThatThrownBy(
            () ->
                FlagFixtures.create(
                    "f",
                    FlagType.NUMBER,
                    List.of(new FlagVariant(a, new JsonValue.JsonNumber(Double.NaN))),
                    a,
                    a))
        .isInstanceOf(FlagwireException.class);
  }

  @Test
  void rejectsAnOffVariantThatDoesNotExist() {
    assertThatThrownBy(
            () ->
                FlagFixtures.create(
                    "f", FlagType.BOOLEAN, booleanVariants(), new VariantKey("missing"), OFF))
        .hasMessageContaining("variant missing does not exist");
  }

  @Test
  void rejectsAFallthroughToAVariantThatDoesNotExist() {
    assertThatThrownBy(
            () ->
                FlagFixtures.create(
                    "f", FlagType.BOOLEAN, booleanVariants(), OFF, new VariantKey("missing")))
        .hasMessageContaining("variant missing does not exist");
  }

  @Test
  void rejectsALongDescription() {
    assertThatThrownBy(
            () ->
                Flag.create(
                    new FlagKey("f"),
                    "x".repeat(501),
                    FlagType.BOOLEAN,
                    booleanVariants(),
                    OFF,
                    OFF,
                    FlagFixtures.salts(),
                    stamp("a")))
        .hasMessageContaining("description");
  }

  @Test
  void rejectsAFlagWithoutEnvironments() {
    assertThatThrownBy(
            () ->
                Flag.create(
                    new FlagKey("f"),
                    "",
                    FlagType.BOOLEAN,
                    booleanVariants(),
                    OFF,
                    OFF,
                    Map.of(),
                    stamp("a")))
        .hasMessageContaining("at least one environment");
  }

  @Test
  void configuresRulesInOrderAndEmitsOneEventForOneEnvironment() {
    Flag flag = booleanFlag("f");
    EnvironmentSettings settings =
        new EnvironmentSettings(
            true,
            OFF,
            List.of(
                rule("second", 1, Serving.fixed(OFF), plan("free")),
                rule("first", 0, Serving.fixed(ON), plan("pro"))),
            Serving.fixed(OFF));

    Transition<Flag> result = flag.configure(DEV, settings, Set.of(), stamp("ann"));

    List<TargetingRule> rules = result.next().requireEnvironment(DEV).rules();
    assertThat(rules).extracting(rule -> rule.id().value()).containsExactly("first", "second");
    assertThat(result.next().revision()).isEqualTo(Revision.of(2));
    assertThat(result.next().updatedAt()).isEqualTo(NOW);
    assertThat(result.events())
        .containsExactly(new DomainEvent.FlagConfigChanged(stamp("ann"), flag.key(), DEV));
    assertThat(result.next().requireEnvironment(STAGING))
        .isEqualTo(flag.requireEnvironment(STAGING));
  }

  @Test
  void rejectsRulesWithTheSameOrder() {
    Flag flag = booleanFlag("f");
    EnvironmentSettings settings =
        settings(true, List.of(rule("a", 0, Serving.fixed(ON)), rule("b", 0, Serving.fixed(OFF))));

    assertThatThrownBy(() -> flag.configure(DEV, settings, Set.of(), stamp("a")))
        .hasMessageContaining("order 0 is used by more than one rule");
  }

  @Test
  void rejectsRulesWhoseOrderHasGaps() {
    Flag flag = booleanFlag("f");
    EnvironmentSettings settings =
        settings(true, List.of(rule("a", 0, Serving.fixed(ON)), rule("b", 2, Serving.fixed(OFF))));

    assertThatThrownBy(() -> flag.configure(DEV, settings, Set.of(), stamp("a")))
        .hasMessageContaining("without gaps");
  }

  @Test
  void rejectsRulesThatShareAnId() {
    Flag flag = booleanFlag("f");
    EnvironmentSettings settings =
        settings(true, List.of(rule("a", 0, Serving.fixed(ON)), rule("a", 1, Serving.fixed(OFF))));

    assertThatThrownBy(() -> flag.configure(DEV, settings, Set.of(), stamp("a")))
        .hasMessageContaining("rule id a is used by more than one rule");
  }

  @Test
  void rejectsMoreThanOneHundredRulesAndNegativeOrders() {
    List<TargetingRule> many =
        IntStream.range(0, 101)
            .mapToObj(index -> rule("r" + index, index, Serving.fixed(ON)))
            .toList();

    assertThatThrownBy(
            () -> booleanFlag("f").configure(DEV, settings(true, many), Set.of(), stamp("a")))
        .hasMessageContaining("at most 100 rules");
    assertThatThrownBy(() -> rule("a", -1, Serving.fixed(ON))).hasMessageContaining("negative");
  }

  @Test
  void rejectsMoreThanFiftyConditionsInOneRule() {
    Condition[] conditions = new Condition[51];
    Arrays.fill(conditions, plan("pro"));

    assertThatThrownBy(() -> rule("a", 0, Serving.fixed(ON), conditions))
        .hasMessageContaining("at most 50 conditions");
  }

  @Test
  void rejectsARuleThatServesAMissingVariant() {
    Flag flag = booleanFlag("f");
    EnvironmentSettings settings =
        settings(true, List.of(rule("a", 0, Serving.fixed(new VariantKey("ghost")))));

    assertThatThrownBy(() -> flag.configure(DEV, settings, Set.of(), stamp("a")))
        .hasMessageContaining("variant ghost does not exist");
  }

  @Test
  void acceptsRolloutsThatSumToExactlyOneHundredThousand() {
    Serving.Rollout rollout =
        new Serving.Rollout(
            List.of(
                new RolloutEntry(ON, Weight.of(33_333)), new RolloutEntry(OFF, Weight.of(66_667))));

    assertThat(rollout.variants()).containsExactlyInAnyOrder(ON, OFF);
  }

  @Test
  void rejectsRolloutsThatDoNotSumToOneHundredThousand() {
    assertThatThrownBy(
            () ->
                new Serving.Rollout(
                    List.of(
                        new RolloutEntry(ON, Weight.of(50_000)),
                        new RolloutEntry(OFF, Weight.of(49_999)))))
        .hasMessageContaining("but sum to 99999");
    assertThatThrownBy(
            () ->
                new Serving.Rollout(
                    List.of(
                        new RolloutEntry(ON, Weight.of(60_000)),
                        new RolloutEntry(OFF, Weight.of(60_000)))))
        .hasMessageContaining("120000");
  }

  @Test
  void rejectsEmptyRolloutsAndRolloutsThatRepeatAVariant() {
    assertThatThrownBy(() -> new Serving.Rollout(List.of()))
        .hasMessageContaining("at least one entry");
    assertThatThrownBy(
            () ->
                new Serving.Rollout(
                    List.of(
                        new RolloutEntry(ON, Weight.of(50_000)),
                        new RolloutEntry(ON, Weight.of(50_000)))))
        .hasMessageContaining("more than once");
  }

  @Test
  void splitsABooleanRolloutByPercentage() {
    Serving.Rollout rollout = Serving.Rollout.split(ON, OFF, Percentage.of(12_345));

    assertThat(rollout.entries())
        .extracting(entry -> entry.weight().units())
        .containsExactly(12_345, 87_655);
  }

  @Test
  void rejectsReferencesToSegmentsThatDoNotExist() {
    Flag flag = booleanFlag("f");
    Condition inBeta = new Condition.SegmentMembership("beta", false);
    EnvironmentSettings settings = settings(true, List.of(rule("a", 0, Serving.fixed(ON), inBeta)));

    assertThatThrownBy(
            () -> flag.configure(DEV, settings, Set.of(new SegmentKey("other")), stamp("a")))
        .hasMessageContaining("unknown segments: beta");
    assertThat(flag.configure(DEV, settings, Set.of(new SegmentKey("beta")), stamp("a")).changed())
        .isTrue();
  }

  @Test
  void reportsTheSegmentsAFlagUsesPerEnvironment() {
    Flag flag =
        booleanFlag("f")
            .configure(
                DEV,
                settings(
                    true,
                    List.of(
                        rule(
                            "a",
                            0,
                            Serving.fixed(ON),
                            new Condition.SegmentMembership("beta", false)))),
                Set.of(new SegmentKey("beta")),
                stamp("a"))
            .next();

    assertThat(flag.referencedSegments(DEV)).containsExactly(new SegmentKey("beta"));
    assertThat(flag.referencedSegments(PROD)).isEmpty();
    assertThat(flag.referencedSegments(new EnvironmentKey("nowhere"))).isEmpty();
  }

  @Test
  void validatesConditionsOfRules() {
    Condition tooManyValues =
        new Condition.Attribute(
            "age",
            Operator.GT,
            List.of(new AttributeValue.NumberValue(1), new AttributeValue.NumberValue(2)),
            false);

    assertThatThrownBy(() -> rule("a", 0, Serving.fixed(ON), tooManyValues))
        .hasMessageContaining("exactly one value");
  }

  @Test
  void togglesOneEnvironmentAndEmitsExactlyOneEvent() {
    Flag flag = booleanFlag("f");

    Transition<Flag> result = flag.toggle(PROD, true, stamp("ann"));

    assertThat(result.next().requireEnvironment(PROD).enabled()).isTrue();
    assertThat(result.next().requireEnvironment(DEV).enabled()).isFalse();
    assertThat(result.events())
        .containsExactly(new DomainEvent.FlagToggled(stamp("ann"), flag.key(), PROD, true));
  }

  @Test
  void emitsNothingWhenATransitionChangesNothing() {
    Flag flag = booleanFlag("f");

    assertThat(flag.toggle(DEV, false, stamp("a")).changed()).isFalse();
    assertThat(flag.toggle(DEV, false, stamp("a")).next()).isSameAs(flag);
    assertThat(flag.releaseKillSwitch(DEV, stamp("a")).changed()).isFalse();
    assertThat(flag.updateDefinition("a flag", booleanVariants(), stamp("a")).changed()).isFalse();
    assertThat(flag.configure(DEV, settings(false, List.of()), Set.of(), stamp("a")).changed())
        .isFalse();
  }

  @Test
  void engagesAndReleasesTheKillSwitchWithOneEventEach() {
    Flag flag = booleanFlag("f").toggle(DEV, true, stamp("a")).next();

    Transition<Flag> engaged = flag.engageKillSwitch(DEV, stamp("ann"));
    Transition<Flag> released = engaged.next().releaseKillSwitch(DEV, stamp("bob"));

    assertThat(engaged.next().requireEnvironment(DEV).killSwitch()).isTrue();
    assertThat(engaged.next().requireEnvironment(DEV).enabled()).isTrue();
    assertThat(engaged.events())
        .containsExactly(new DomainEvent.KillSwitchEngaged(stamp("ann"), flag.key(), DEV));
    assertThat(engaged.next().engageKillSwitch(DEV, stamp("a")).changed()).isFalse();
    assertThat(released.next().requireEnvironment(DEV).killSwitch()).isFalse();
    assertThat(released.events())
        .containsExactly(new DomainEvent.KillSwitchReleased(stamp("bob"), flag.key(), DEV));
  }

  @Test
  void keepsTheKillSwitchWhenTheConfigurationIsReplaced() {
    Flag engaged = booleanFlag("f").engageKillSwitch(DEV, stamp("a")).next();

    Flag configured =
        engaged.configure(DEV, settings(true, List.of()), Set.of(), stamp("a")).next();

    assertThat(configured.requireEnvironment(DEV).killSwitch()).isTrue();
    assertThat(configured.requireEnvironment(DEV).salt()).isEqualTo(new Salt("a1"));
  }

  @Test
  void rejectsAnUnknownEnvironment() {
    Flag flag = booleanFlag("f");
    EnvironmentKey nowhere = new EnvironmentKey("nowhere");

    assertThatThrownBy(() -> flag.toggle(nowhere, true, stamp("a")))
        .isInstanceOf(FlagwireException.class)
        .extracting(thrown -> ((FlagwireException) thrown).error())
        .isInstanceOf(FlagwireError.NotFound.class);
  }

  @Test
  void changesTheDefinitionInEveryEnvironmentWithOneEvent() {
    VariantKey a = new VariantKey("a");
    VariantKey b = new VariantKey("b");
    Flag flag =
        FlagFixtures.create(
                "f",
                FlagType.STRING,
                List.of(
                    new FlagVariant(a, new JsonValue.JsonString("A")),
                    new FlagVariant(b, new JsonValue.JsonString("B"))),
                a,
                a)
            .next();
    List<FlagVariant> extended =
        List.of(
            new FlagVariant(a, new JsonValue.JsonString("A")),
            new FlagVariant(b, new JsonValue.JsonString("B")),
            new FlagVariant(new VariantKey("c"), new JsonValue.JsonString("C")));

    Transition<Flag> result = flag.updateDefinition("new text", extended, stamp("ann"));

    assertThat(result.next().description()).isEqualTo("new text");
    assertThat(result.next().variants()).hasSize(3);
    assertThat(result.events())
        .containsExactly(
            new DomainEvent.FlagDefinitionChanged(
                stamp("ann"), flag.key(), Set.of(DEV, STAGING, PROD)));
  }

  @Test
  void refusesToRemoveAVariantThatSomeEnvironmentStillServes() {
    Flag flag = booleanFlag("f");
    List<FlagVariant> withoutOff = List.of(new FlagVariant(ON, new JsonValue.JsonBoolean(true)));

    assertThatThrownBy(() -> flag.updateDefinition("a flag", withoutOff, stamp("a")))
        .hasMessageContaining("variant off does not exist");
  }

  @Test
  void refusesAVariantValueOfTheWrongTypeInADefinitionChange() {
    Flag flag = booleanFlag("f");
    List<FlagVariant> wrong =
        List.of(
            new FlagVariant(ON, new JsonValue.JsonString("yes")),
            new FlagVariant(OFF, new JsonValue.JsonBoolean(false)));

    assertThatThrownBy(() -> flag.updateDefinition("a flag", wrong, stamp("a")))
        .hasMessageContaining("does not match the flag type");
  }

  @Test
  void archivesOnceAndThenRejectsEveryEdit() {
    Flag flag = booleanFlag("f");

    Transition<Flag> archived = flag.archive(stamp("ann"));

    assertThat(archived.next().archived()).isTrue();
    assertThat(archived.events())
        .containsExactly(
            new DomainEvent.FlagArchived(stamp("ann"), flag.key(), Set.of(DEV, STAGING, PROD)));
    Flag gone = archived.next();
    List<Runnable> edits =
        List.of(
            () -> gone.archive(stamp("a")),
            () -> gone.toggle(DEV, true, stamp("a")),
            () -> gone.engageKillSwitch(DEV, stamp("a")),
            () -> gone.releaseKillSwitch(DEV, stamp("a")),
            () -> gone.updateDefinition("x", booleanVariants(), stamp("a")),
            () -> gone.configure(DEV, settings(true, List.of()), Set.of(), stamp("a")));
    edits.forEach(
        edit ->
            assertThatThrownBy(edit::run)
                .isInstanceOf(FlagwireException.class)
                .hasMessageContaining("is archived")
                .extracting(thrown -> ((FlagwireException) thrown).error())
                .isInstanceOf(FlagwireError.Conflict.class));
  }

  @Test
  void addsAnEnvironmentThatStartsOff() {
    Flag flag = booleanFlag("f");
    EnvironmentKey qa = new EnvironmentKey("qa");

    Flag extended = flag.withEnvironment(qa, new Salt("d4"), NOW.plusMinutes(1));

    assertThat(extended.requireEnvironment(qa).enabled()).isFalse();
    assertThat(extended.requireEnvironment(qa).offVariant()).isEqualTo(OFF);
    assertThat(extended.revision()).isEqualTo(Revision.of(2));
    assertThat(extended.updatedAt()).isEqualTo(NOW.plusMinutes(1));
    assertThatThrownBy(() -> flag.withEnvironment(DEV, new Salt("d4"), NOW))
        .hasMessageContaining("already has environment");
  }

  @Test
  void rejectsAStoredFlagWithoutARevision() {
    Flag flag = booleanFlag("f");
    ZonedDateTime at = NOW;

    assertThatThrownBy(
            () ->
                new Flag(
                    flag.key(),
                    "",
                    flag.type(),
                    flag.variants(),
                    flag.environments(),
                    false,
                    Revision.NONE,
                    at,
                    at))
        .hasMessageContaining("revision");
  }

  @Test
  void findsVariantsByKey() {
    Flag flag = booleanFlag("f");

    assertThat(flag.variant(ON)).isPresent();
    assertThat(flag.variant(new VariantKey("ghost"))).isEmpty();
    assertThat(flag.environment(new EnvironmentKey("nowhere"))).isEmpty();
  }
}
