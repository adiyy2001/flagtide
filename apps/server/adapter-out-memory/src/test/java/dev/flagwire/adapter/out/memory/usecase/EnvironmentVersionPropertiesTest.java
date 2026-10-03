package dev.flagwire.adapter.out.memory.usecase;

import static dev.flagwire.adapter.out.memory.usecase.Flagwire.DEV;
import static dev.flagwire.adapter.out.memory.usecase.Flagwire.PROD;
import static dev.flagwire.adapter.out.memory.usecase.Flagwire.STAGING;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagwire.application.security.Principal;
import dev.flagwire.application.usecase.ArchiveFlag;
import dev.flagwire.application.usecase.CommandResult;
import dev.flagwire.application.usecase.ConfigureFlag;
import dev.flagwire.application.usecase.DeleteSegment;
import dev.flagwire.application.usecase.EngageKillSwitch;
import dev.flagwire.application.usecase.ReadAuditLog;
import dev.flagwire.application.usecase.ReleaseKillSwitch;
import dev.flagwire.application.usecase.ToggleFlag;
import dev.flagwire.domain.audit.EntityType;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentVersion;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.SegmentKey;
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

  private CommandResult<?> run(Flagwire app, Step step) {
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
    Flagwire app = new Flagwire();
    Map<EnvironmentKey, Long> last = new HashMap<>();
    ENVIRONMENTS.forEach(environment -> last.put(environment, 0L));

    for (Step step : steps) {
      CommandResult<?> result;
      try {
        result = this.run(app, step);
      } catch (FlagwireException rejected) {
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
    Flagwire app = new Flagwire();
    for (Step step : steps) {
      try {
        this.run(app, step);
      } catch (FlagwireException rejected) {
        continue;
      }
    }

    var entries = app.readAuditLog.execute(app.admin(DEV), ReadAuditLog.Query.newest(500));

    entries.stream()
        .filter(entry -> entry.entityType() != EntityType.PROJECT)
        .forEach(entry -> assertThat(entry.environmentVersion()).isPresent());
  }
}
