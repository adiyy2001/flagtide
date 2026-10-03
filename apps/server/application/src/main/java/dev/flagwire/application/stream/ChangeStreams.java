package dev.flagwire.application.stream;

import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.ChangeNotification;
import dev.flagwire.application.port.out.PropagationStats;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.EnvironmentVersion;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ChangeStreams {

  private final ChangeLog changeLog;
  private final SnapshotSource snapshots;
  private final PropagationStats stats;
  private final TimeSource timeSource;
  private final int ringCapacity;
  private final Map<EnvironmentRef, EnvironmentStream> streams = new ConcurrentHashMap<>();

  public ChangeStreams(
      ChangeLog changeLog,
      SnapshotSource snapshots,
      PropagationStats stats,
      TimeSource timeSource,
      int ringCapacity) {
    if (ringCapacity < 1) {
      throw new IllegalArgumentException("the ring keeps at least one entry");
    }
    this.changeLog = changeLog;
    this.snapshots = snapshots;
    this.stats = stats;
    this.timeSource = timeSource;
    this.ringCapacity = ringCapacity;
  }

  public StreamRegistration connect(
      Principal principal, Optional<EnvironmentVersion> since, StreamClient client) {
    EnvironmentRef reference = principal.environmentRef();
    EnvironmentStream stream =
        this.streams.computeIfAbsent(
            reference,
            key ->
                new EnvironmentStream(
                    key, this.changeLog, this.snapshots, this.stats, this.ringCapacity));
    EnvironmentStream.Member member = stream.join(client, since);
    this.stats.clientConnected(reference);
    return new StreamRegistration(
        stream, member, this.timeSource::now, () -> this.stats.clientDisconnected(reference));
  }

  public void propagate(ChangeNotification notification) {
    EnvironmentStream stream = this.streams.get(notification.environment());
    if (stream != null && notification.version().value() > stream.latestVersion()) {
      stream.catchUpFromDatabase();
    }
  }

  public void resync() {
    this.streams.values().forEach(EnvironmentStream::catchUpFromDatabase);
  }

  public void heartbeat() {
    var now = this.timeSource.now();
    this.streams.values().forEach(stream -> stream.heartbeat(now));
  }
}
