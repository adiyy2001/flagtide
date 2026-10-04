package dev.flagtide.application.stream;

import dev.flagtide.application.stream.EnvironmentStream.Member;
import dev.flagtide.domain.value.EnvironmentVersion;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class StreamRegistration implements AutoCloseable {

  private final EnvironmentStream stream;
  private final Member member;
  private final Supplier<ZonedDateTime> clock;
  private final Runnable onClose;
  private final AtomicBoolean closed = new AtomicBoolean();

  StreamRegistration(
      EnvironmentStream stream, Member member, Supplier<ZonedDateTime> clock, Runnable onClose) {
    this.stream = stream;
    this.member = member;
    this.clock = clock;
    this.onClose = onClose;
  }

  public void acknowledge(EnvironmentVersion version) {
    if (!this.closed.get()) {
      this.stream.acknowledge(this.member, version.value(), this.clock.get());
    }
  }

  @Override
  public void close() {
    if (this.closed.compareAndSet(false, true)) {
      this.stream.leave(this.member);
      this.onClose.run();
    }
  }
}
