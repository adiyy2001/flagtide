package dev.flagwire.bootstrap;

import dev.flagwire.application.port.out.IdGenerator;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.support.SecureIdGenerator;
import dev.flagwire.application.support.SystemTimeSource;
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
      @ConfigProperty(name = "flagwire.instance.id") Optional<String> configured) {
    return new InstanceId(
        configured.filter(id -> !id.isBlank()).orElseGet(() -> UUID.randomUUID().toString()));
  }
}
