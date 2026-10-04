package dev.flagtide.domain.leaky;

import dev.flagtide.application.support.SystemTimeSource;
import java.io.File;

public final class LeakyDomainClass {

  private final File file = new File("x");
  private final SystemTimeSource time = new SystemTimeSource();

  public File file() {
    return this.file;
  }

  public SystemTimeSource time() {
    return this.time;
  }
}
