package dev.flagwire.bootstrap;

import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.ProjectRepository;
import dev.flagwire.application.port.out.PropagationStats;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.stream.ChangeStreams;
import dev.flagwire.application.usecase.Authenticate;
import dev.flagwire.application.usecase.BuildSnapshot;
import dev.flagwire.application.usecase.ConnectClient;
import dev.flagwire.application.usecase.GetPropagation;
import dev.flagwire.application.usecase.PropagateChange;
import dev.flagwire.application.usecase.SendHeartbeats;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class StreamWiring {

  @Produces
  @Singleton
  ChangeStreams changeStreams(
      ChangeLog changeLog,
      BuildSnapshot buildSnapshot,
      PropagationStats stats,
      TimeSource timeSource,
      @ConfigProperty(name = "flagwire.propagation.ring-capacity") int ringCapacity) {
    return new ChangeStreams(
        changeLog, buildSnapshot::forEnvironment, stats, timeSource, ringCapacity);
  }

  @Produces
  @Singleton
  ConnectClient connectClient(
      ApiKeyStore apiKeys,
      ChangeStreams streams,
      TimeSource timeSource,
      @ConfigProperty(name = "flagwire.propagation.key-cache-ttl") Duration keyCacheTtl) {
    return new ConnectClient(new Authenticate(apiKeys), streams, timeSource, keyCacheTtl);
  }

  @Produces
  @Singleton
  PropagateChange propagateChange(ChangeStreams streams) {
    return new PropagateChange(streams);
  }

  @Produces
  @Singleton
  SendHeartbeats sendHeartbeats(ChangeStreams streams) {
    return new SendHeartbeats(streams);
  }

  @Produces
  @Singleton
  GetPropagation getPropagation(
      ProjectRepository projects, PropagationStats stats, Authorizer authorizer) {
    return new GetPropagation(projects, stats, authorizer);
  }
}
