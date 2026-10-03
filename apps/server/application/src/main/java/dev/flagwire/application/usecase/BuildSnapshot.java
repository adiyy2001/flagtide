package dev.flagwire.application.usecase;

import dev.flagwire.application.change.ChangeLogEntry;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.FlagRepository;
import dev.flagwire.application.port.out.SegmentRepository;
import dev.flagwire.application.port.out.TransactionRunner;
import dev.flagwire.application.security.Principal;
import dev.flagwire.application.sync.Snapshot;
import dev.flagwire.domain.evaluation.FlagConfig;
import dev.flagwire.domain.evaluation.Segment;
import dev.flagwire.domain.flag.Flag;
import dev.flagwire.domain.flag.FlagCompiler;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.EnvironmentVersion;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class BuildSnapshot {

  private final TransactionRunner transactions;
  private final FlagRepository flags;
  private final SegmentRepository segments;
  private final ChangeLog changeLog;

  public BuildSnapshot(
      TransactionRunner transactions,
      FlagRepository flags,
      SegmentRepository segments,
      ChangeLog changeLog) {
    this.transactions = transactions;
    this.flags = flags;
    this.segments = segments;
    this.changeLog = changeLog;
  }

  public Snapshot execute(Principal principal) {
    return this.transactions.inReadOnlyTransaction(() -> this.build(principal.environmentRef()));
  }

  Snapshot build(EnvironmentRef scope) {
    EnvironmentVersion version = this.changeLog.currentVersion(scope);
    Optional<ChangeLogEntry> latest =
        this.changeLog
            .entriesAfter(scope, EnvironmentVersion.of(Math.max(version.value() - 1, 0)))
            .stream()
            .filter(entry -> entry.version().equals(version))
            .findFirst();
    List<FlagConfig> configs =
        this.flags.findAll(scope.project()).stream()
            .filter(flag -> !flag.archived())
            .sorted(Comparator.comparing(Flag::key))
            .map(flag -> FlagCompiler.compile(flag, scope.environment()))
            .toList();
    List<Segment> compiledSegments =
        this.segments.findAll(scope).stream().map(FlagCompiler::compileSegment).toList();
    return new Snapshot(
        version, latest.map(ChangeLogEntry::committedAt), configs, compiledSegments);
  }
}
