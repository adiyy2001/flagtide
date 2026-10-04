package dev.flagtide.application.stream;

import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.value.EnvironmentVersion;

public record StreamSession(Principal principal, StreamRegistration registration)
    implements AutoCloseable {

  public void acknowledge(EnvironmentVersion version) {
    this.registration.acknowledge(version);
  }

  @Override
  public void close() {
    this.registration.close();
  }
}
