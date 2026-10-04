package dev.flagtide.application.port.out;

import dev.flagtide.domain.value.EnvironmentRef;

public interface PropagationStats {

  void clientConnected(EnvironmentRef environment);

  void clientDisconnected(EnvironmentRef environment);

  void recordAcknowledgement(EnvironmentRef environment, long latencyMillis);

  PropagationReport report(EnvironmentRef environment);

  default void publish() {}

  default void withdraw() {}
}
