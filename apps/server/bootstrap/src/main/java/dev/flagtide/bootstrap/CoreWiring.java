package dev.flagtide.bootstrap;

import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.support.SecureIdGenerator;
import dev.flagtide.application.support.SystemTimeSource;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class CoreWiring {

  @Produces
  @Singleton
  TimeSource timeSource() {
    return new SystemTimeSource();
  }

  @Produces
  @Singleton
  IdGenerator idGenerator() {
    return new SecureIdGenerator();
  }

  @Produces
  @Singleton
  Authorizer authorizer() {
    return new Authorizer();
  }

  @Produces
  @Singleton
  InstanceId instanceId(
      @ConfigProperty(name = "flagtide.instance.id") Optional<String> configured) {
    return new InstanceId(
        configured.filter(id -> !id.isBlank()).orElseGet(() -> UUID.randomUUID().toString()));
  }
}
