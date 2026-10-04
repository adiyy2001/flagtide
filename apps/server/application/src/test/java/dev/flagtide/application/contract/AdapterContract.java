package dev.flagtide.application.contract;

import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.testing.MutableTimeSource;
import dev.flagtide.application.testing.Samples;
import dev.flagtide.application.testing.TestAdapters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AdapterContract {

  protected static final int DEFAULT_RETENTION = 1000;

  protected MutableTimeSource time;
  protected TestAdapters adapters;

  protected abstract TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention);

  @BeforeEach
  void createFreshAdapters() {
    this.time = new MutableTimeSource(Samples.NOW);
    this.adapters = this.createAdapters(this.time, DEFAULT_RETENTION);
  }

  @AfterEach
  void releaseAdapters() throws Exception {
    if (this.adapters instanceof AutoCloseable closeable) {
      closeable.close();
    }
  }
}
