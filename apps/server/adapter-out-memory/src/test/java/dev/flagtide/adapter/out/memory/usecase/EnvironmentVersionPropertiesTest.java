package dev.flagtide.adapter.out.memory.usecase;

import static dev.flagtide.adapter.out.memory.usecase.Flagtide.DEV;
import static dev.flagtide.adapter.out.memory.usecase.Flagtide.PROD;
import static dev.flagtide.adapter.out.memory.usecase.Flagtide.STAGING;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagtide.application.security.Principal;
import dev.flagtide.application.usecase.ArchiveFlag;
import dev.flagtide.application.usecase.CommandResult;
import dev.flagtide.application.usecase.ConfigureFlag;
import dev.flagtide.application.usecase.DeleteSegment;
import dev.flagtide.application.usecase.EngageKillSwitch;
import dev.flagtide.application.usecase.ReadAuditLog;
import dev.flagtide.application.usecase.ReleaseKillSwitch;
import dev.flagtide.application.usecase.ToggleFlag;
import dev.flagtide.domain.audit.EntityType;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentVersion;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.SegmentKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class EnvironmentVersionPropertiesTest {

  record Step(int kind, int flag, int environment, boolean flagValue) {}

  private static final List<EnvironmentKey> ENVIRONMENTS = List.of(DEV, STAGING, PROD);

  @Provide
  Arbitrary<List<Step>> steps() {
    return Arbitraries.integers()
        .between(0, 8)
        .flatMap(
            kind ->
                Arbitraries.integers()
                    .between(0, 2)
                    .flatMap(
                        flag ->
                            Arbitraries.integers()
                                .between(0, 2)
                                .flatMap(
                                    environment ->
                                        Arbitraries.of(true, false)
                                            .map(
                                                value ->
                                                    new Step(kind, flag, environment, value)))))
        .list()
        .ofMinSize(1)
        .ofMaxSize(40);
  }

  private static FlagKey flagKey(int index) {
    return new FlagKey("flag-" + index);
  }

  private CommandResult<?> run(Flagtide app, Step step) {
    EnvironmentKey environment = ENVIRONMENTS.get(step.environment());
    Principal admin = app.admin(environment);
    FlagKey flag = flagKey(step.flag());
    return switch (step.kind()) {
      case 0, 1 -> app.createFlag.execute(admin, Fixtures.booleanFlag(flag.value()));
      case 2 ->
          app.toggleFlag.execute(
              admin, new ToggleFlag.Command(flag, Optional.empty(), environment, step.flagValue()));
      case 3 ->
          app.engageKillSwitch.execute(admin, new EngageKillSwitch.Command(flag, environment));
      case 4 ->
          app.releaseKillSwitch.execute(admin, new ReleaseKillSwitch.Command(flag, environment));
      case 5 -> app.archiveFlag.execute(admin, new ArchiveFlag.Command(flag, Optional.empty()));
      case 6 ->
          app.saveSegment.execute(admin, Fixtures.segment(environment, "seg", "u" + step.flag()));
      case 7 ->
          app.deleteSegment.execute(
              admin,
              new DeleteSegment.Command(environment, new SegmentKey("seg"), Optional.empty()));
      default ->
          app.configureFlag.execute(
              admin,
              new ConfigureFlag.Command(
                  flag,
                  Optional.empty(),
                  environment,
                  Fixtures.rolloutBehindSegment("seg", 50_000, step.flagValue())));
    };
  }

  @Property(tries = 60)
  void environmentVersionsOnlyEverIncreaseByOne(@ForAll("steps") List<Step> steps) {
    Flagtide app = new Flagtide();
    Map<EnvironmentKey, Long> last = new HashMap<>();
    ENVIRONMENTS.forEach(environment -> last.put(environment, 0L));

    for (Step step : steps) {
      CommandResult<?> result;
      try {
        result = this.run(app, step);
      } catch (FlagtideException rejected) {
        continue;
      }
      result
          .versions()
          .forEach(
              (environment, version) -> {
                assertThat(version.value()).isEqualTo(last.get(environment) + 1);
                last.put(environment, version.value());
              });
    }

    ENVIRONMENTS.forEach(
        environment -> {
          var ref = app.admin(environment).environmentRef();
          assertThat(app.adapters.changeLog().currentVersion(ref).value())
              .isEqualTo(last.get(environment));
          assertThat(
                  app.adapters.changeLog().entriesAfter(ref, EnvironmentVersion.ZERO).stream()
                      .map(entry -> entry.version().value())
                      .toList())
              .isEqualTo(LongStream.rangeClosed(1, last.get(environment)).boxed().toList());
        });
  }

  @Property(tries = 60)
  void everyEntityAuditEntryCarriesTheVersionOfItsChange(@ForAll("steps") List<Step> steps) {
    Flagtide app = new Flagtide();
    for (Step step : steps) {
      try {
        this.run(app, step);
      } catch (FlagtideException rejected) {
        continue;
      }
    }

    var entries = app.readAuditLog.execute(app.admin(DEV), ReadAuditLog.Query.newest(500));

    entries.stream()
        .filter(entry -> entry.entityType() != EntityType.PROJECT)
        .forEach(entry -> assertThat(entry.environmentVersion()).isPresent());
  }
}
