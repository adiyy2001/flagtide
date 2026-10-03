package dev.flagwire.application.stream;

import dev.flagwire.application.change.ChangeLogEntry;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.PropagationStats;
import dev.flagwire.application.sync.Snapshot;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.EnvironmentVersion;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class EnvironmentStream {

  static final class Member {

    private final StreamClient client;
    private long sent;
    private long welcomed;
    private long acknowledged;

    Member(StreamClient client) {
      this.client = client;
    }
  }

  private record Frame(String text, long version) {}

  private final EnvironmentRef reference;
  private final ChangeLog changeLog;
  private final SnapshotSource snapshots;
  private final PropagationStats stats;
  private final int capacity;

  private final List<ChangeLogEntry> ring = new ArrayList<>();
  private final Map<Long, String> catchUpFrames = new HashMap<>();
  private final Set<Member> members = new LinkedHashSet<>();
  private Frame cachedSnapshot;
  private boolean loaded;
  private volatile long latest;

  EnvironmentStream(
      EnvironmentRef reference,
      ChangeLog changeLog,
      SnapshotSource snapshots,
      PropagationStats stats,
      int capacity) {
    this.reference = reference;
    this.changeLog = changeLog;
    this.snapshots = snapshots;
    this.stats = stats;
    this.capacity = capacity;
  }

  long latestVersion() {
    return this.latest;
  }

  synchronized Member join(StreamClient client, Optional<EnvironmentVersion> since) {
    this.load();
    if (since.isPresent() && since.get().value() > this.latest) {
      this.catchUp();
    }
    Member member = new Member(client);
    Frame welcome = this.frameAfter(since.map(EnvironmentVersion::value));
    member.sent = welcome.version();
    member.welcomed = welcome.version();
    this.members.add(member);
    client.send(welcome.text());
    return member;
  }

  synchronized void leave(Member member) {
    this.members.remove(member);
  }

  synchronized void catchUpFromDatabase() {
    this.load();
    this.catchUp();
  }

  synchronized void heartbeat(ZonedDateTime now) {
    if (this.members.isEmpty()) {
      return;
    }
    String frame = Frames.heartbeat(now, this.latest);
    this.members.forEach(member -> member.client.send(frame));
  }

  synchronized void acknowledge(Member member, long version, ZonedDateTime acknowledgedAt) {
    if (version <= member.acknowledged || version > member.sent) {
      return;
    }
    member.acknowledged = version;
    if (version <= member.welcomed) {
      return;
    }
    this.committedAt(version)
        .ifPresent(
            committedAt ->
                this.stats.recordAcknowledgement(
                    this.reference, Duration.between(committedAt, acknowledgedAt).toMillis()));
  }

  private void load() {
    if (this.loaded) {
      return;
    }
    long current = this.changeLog.currentVersion(this.reference).value();
    List<ChangeLogEntry> entries =
        this.changeLog.entriesAfter(
            this.reference, EnvironmentVersion.of(Math.max(0, current - this.capacity)));
    this.ring.addAll(entries);
    this.latest = entries.isEmpty() ? current : entries.getLast().version().value();
    this.loaded = true;
  }

  private void catchUp() {
    List<ChangeLogEntry> entries =
        this.changeLog.entriesAfter(this.reference, EnvironmentVersion.of(this.latest));
    if (entries.isEmpty()) {
      return;
    }
    if (entries.getFirst().version().value() != this.latest + 1) {
      this.restartFromSnapshot();
      return;
    }
    this.append(entries);
  }

  private void append(List<ChangeLogEntry> entries) {
    long from = this.latest;
    this.ring.addAll(entries);
    if (this.ring.size() > this.capacity) {
      this.ring.subList(0, this.ring.size() - this.capacity).clear();
    }
    this.latest = entries.getLast().version().value();
    this.catchUpFrames.clear();
    if (this.members.isEmpty()) {
      return;
    }
    String shared = Frames.deltas(from, this.latest, entries);
    this.members.forEach(member -> this.deliver(member, from, shared));
  }

  private void deliver(Member member, long from, String shared) {
    if (member.sent >= this.latest) {
      return;
    }
    if (member.sent == from) {
      member.sent = this.latest;
      member.client.send(shared);
      return;
    }
    Frame frame = this.frameAfter(Optional.of(member.sent));
    member.sent = frame.version();
    member.client.send(frame.text());
  }

  private void restartFromSnapshot() {
    this.ring.clear();
    this.catchUpFrames.clear();
    this.cachedSnapshot = null;
    this.latest = this.changeLog.currentVersion(this.reference).value();
    Frame snapshot = this.snapshotFrame();
    this.members.forEach(
        member -> {
          member.sent = snapshot.version();
          member.welcomed = snapshot.version();
          member.client.send(snapshot.text());
        });
  }

  private Frame frameAfter(Optional<Long> since) {
    if (since.isPresent() && since.get() == this.latest) {
      return new Frame(Frames.deltas(this.latest, this.latest, List.of()), this.latest);
    }
    if (since.isPresent() && since.get() < this.latest && this.ringCovers(since.get())) {
      return new Frame(
          this.catchUpFrames.computeIfAbsent(since.get(), this::catchUpFrame), this.latest);
    }
    return this.snapshotFrame();
  }

  private String catchUpFrame(long since) {
    List<ChangeLogEntry> missed =
        this.ring.stream().filter(entry -> entry.version().value() > since).toList();
    return Frames.deltas(since, this.latest, missed);
  }

  private boolean ringCovers(long since) {
    return !this.ring.isEmpty() && this.ring.getFirst().version().value() <= since + 1;
  }

  private Frame snapshotFrame() {
    if (this.cachedSnapshot != null && this.cachedSnapshot.version() == this.latest) {
      return this.cachedSnapshot;
    }
    Snapshot snapshot = this.snapshots.forEnvironment(this.reference);
    this.cachedSnapshot = new Frame(Frames.snapshot(snapshot), snapshot.version().value());
    return this.cachedSnapshot;
  }

  private Optional<ZonedDateTime> committedAt(long version) {
    if (this.ring.isEmpty()) {
      return Optional.empty();
    }
    long index = version - this.ring.getFirst().version().value();
    if (index < 0 || index >= this.ring.size()) {
      return Optional.empty();
    }
    return Optional.of(this.ring.get((int) index).committedAt());
  }
}
