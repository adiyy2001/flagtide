package dev.flagwire.application.stream;

import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.value.EnvironmentVersion;

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
