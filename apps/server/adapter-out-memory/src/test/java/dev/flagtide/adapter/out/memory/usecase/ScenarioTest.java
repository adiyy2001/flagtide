package dev.flagtide.adapter.out.memory.usecase;

import static dev.flagtide.adapter.out.memory.usecase.Flagtide.DEV;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagtide.application.change.Change;
import dev.flagtide.application.change.ChangeLogEntry;
import dev.flagtide.application.security.Principal;
import dev.flagtide.application.sync.Snapshot;
import dev.flagtide.application.sync.SyncResult;
import dev.flagtide.application.usecase.ConfigureFlag;
import dev.flagtide.application.usecase.EngageKillSwitch;
import dev.flagtide.application.usecase.ReleaseKillSwitch;
import dev.flagtide.application.usecase.SaveSegment;
import dev.flagtide.application.usecase.ToggleFlag;
import dev.flagtide.domain.evaluation.EvaluationContext;
import dev.flagtide.domain.evaluation.EvaluationResult;
import dev.flagtide.domain.evaluation.Evaluator;
import dev.flagtide.domain.evaluation.FlagConfig;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.evaluation.Operator;
import dev.flagtide.domain.evaluation.Reason;
import dev.flagtide.domain.evaluation.Segment;
import dev.flagtide.domain.value.EnvironmentVersion;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.SegmentKey;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ScenarioTest {

  private static final FlagKey CHECKOUT = new FlagKey("new-checkout");

  private final Flagtide app = new Flagtide();
  private final Principal admin = this.app.admin(DEV);
  private final Principal sdk = this.app.sdk(DEV);
  private final Evaluator evaluator = new Evaluator();

  private final Map<String, FlagConfig> clientFlags = new HashMap<>();
  private final Map<String, Segment> clientSegments = new HashMap<>();
  private EnvironmentVersion clientVersion = EnvironmentVersion.ZERO;

  private EvaluationContext user(String key, String plan) {
    return EvaluationContext.of(key, Map.of("plan", new JsonValue.JsonString(plan)));
  }

  private EvaluationResult evaluate(EvaluationContext context) {
    return this.evaluator.evaluate(
        this.clientFlags.get(CHECKOUT.value()), context, Map.copyOf(this.clientSegments));
  }

  private void loadSnapshot(Snapshot snapshot) {
    this.clientFlags.clear();
    this.clientSegments.clear();
    snapshot.flags().forEach(flag -> this.clientFlags.put(flag.key(), flag));
    snapshot.segments().forEach(segment -> this.clientSegments.put(segment.key(), segment));
    this.clientVersion = snapshot.version();
  }

  private void applyDeltas(SyncResult.Deltas deltas) {
    for (ChangeLogEntry entry : deltas.entries()) {
      for (Change change : entry.changes()) {
        switch (change) {
          case Change.FlagUpserted upsert ->
              this.clientFlags.put(upsert.config().key(), upsert.config());
          case Change.FlagRemoved removed -> this.clientFlags.remove(removed.key());
          case Change.SegmentUpserted upsert ->
              this.clientSegments.put(upsert.segment().key(), upsert.segment());
          case Change.SegmentRemoved removed -> this.clientSegments.remove(removed.key());
        }
      }
    }
    this.clientVersion = deltas.to();
  }

  @Test
  void aFlagGoesFromCreationThroughASegmentRolloutToDeltasThatReachTheClient() {
    Fixtures.createFlag(this.app, CHECKOUT.value());
    this.app.saveSegment.execute(this.admin, Fixtures.betaTesters(DEV));
    this.app.configureFlag.execute(
        this.admin,
        new ConfigureFlag.Command(
            CHECKOUT, Optional.empty(), DEV, Fixtures.rolloutBehindSegment("beta", 30_000, true)));

    this.loadSnapshot(this.app.buildSnapshot.execute(this.sdk));

    assertThat(this.clientVersion).isEqualTo(EnvironmentVersion.of(3));
    EvaluationResult outsider = this.evaluate(this.user("anyone", "free"));
    assertThat(outsider.reason()).isEqualTo(Reason.FALLTHROUGH);
    assertThat(outsider.variantKey()).isEqualTo("off");
    long inRollout =
        IntStream.range(0, 4000)
            .mapToObj(index -> this.evaluate(this.user("user-" + index, "beta")))
            .filter(result -> result.variantKey().equals("on"))
            .peek(result -> assertThat(result.reason()).isEqualTo(Reason.RULE_MATCH))
            .count();
    assertThat(inRollout).isBetween(1000L, 1400L);
    assertThat(this.evaluate(this.user("user-7", "beta")).ruleId()).contains("beta-rollout");

    this.app.time.advance(Duration.ofSeconds(30));
    this.app.engageKillSwitch.execute(this.admin, new EngageKillSwitch.Command(CHECKOUT, DEV));
    this.app.saveSegment.execute(
        this.admin,
        new SaveSegment.Command(
            DEV,
            new SegmentKey("beta"),
            Optional.empty(),
            "Beta testers",
            Set.of("vip"),
            Set.of(),
            List.of(List.of(Fixtures.attribute("plan", Operator.EQUALS, "beta")))));

    SyncResult result = this.app.syncSince.execute(this.sdk, Optional.of(this.clientVersion));

    assertThat(result)
        .isInstanceOfSatisfying(
            SyncResult.Deltas.class,
            deltas -> {
              assertThat(deltas.from()).isEqualTo(EnvironmentVersion.of(3));
              assertThat(deltas.to()).isEqualTo(EnvironmentVersion.of(5));
              assertThat(deltas.entries()).hasSize(2);
              this.applyDeltas(deltas);
            });
    assertThat(this.evaluate(this.user("user-7", "beta")).reason()).isEqualTo(Reason.KILL_SWITCH);
    assertThat(this.evaluate(this.user("vip", "free")).variantKey()).isEqualTo("off");
    assertThat(this.clientSegments.get("beta").included()).containsExactly("vip");

    this.app.releaseKillSwitch.execute(this.admin, new ReleaseKillSwitch.Command(CHECKOUT, DEV));
    this.app.toggleFlag.execute(
        this.admin, new ToggleFlag.Command(CHECKOUT, Optional.empty(), DEV, false));
    SyncResult next = this.app.syncSince.execute(this.sdk, Optional.of(this.clientVersion));
    assertThat(next).isInstanceOfSatisfying(SyncResult.Deltas.class, this::applyDeltas);
    assertThat(this.evaluate(this.user("user-7", "beta")).reason()).isEqualTo(Reason.OFF);

    Snapshot fresh = this.app.buildSnapshot.execute(this.sdk);
    assertThat(fresh.version()).isEqualTo(this.clientVersion);
    assertThat(fresh.flags()).containsExactlyElementsOf(this.clientFlags.values());
  }
}
