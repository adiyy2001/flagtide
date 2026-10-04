package dev.flagtide.application.testing;

import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.domain.value.Salt;
import java.util.concurrent.atomic.AtomicLong;

public final class SequentialIds implements IdGenerator {

  private final AtomicLong counter = new AtomicLong();

  @Override
  public String newId() {
    return "id-" + this.counter.incrementAndGet();
  }

  @Override
  public Salt newSalt() {
    return new Salt(Long.toHexString(0x1000 + this.counter.incrementAndGet()));
  }

  @Override
  public String newToken() {
    return "token" + this.counter.incrementAndGet();
  }
}
