package dev.flagtide.bootstrap;

import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.application.port.out.PropagationStats;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.stream.ChangeStreams;
import dev.flagtide.application.usecase.Authenticate;
import dev.flagtide.application.usecase.BuildSnapshot;
import dev.flagtide.application.usecase.ConnectClient;
import dev.flagtide.application.usecase.GetPropagation;
import dev.flagtide.application.usecase.PropagateChange;
import dev.flagtide.application.usecase.SendHeartbeats;
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
      @ConfigProperty(name = "flagtide.propagation.ring-capacity") int ringCapacity) {
    return new ChangeStreams(
        changeLog, buildSnapshot::forEnvironment, stats, timeSource, ringCapacity);
  }

  @Produces
  @Singleton
  ConnectClient connectClient(
      ApiKeyStore apiKeys,
      ChangeStreams streams,
      TimeSource timeSource,
      @ConfigProperty(name = "flagtide.propagation.key-cache-ttl") Duration keyCacheTtl) {
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
