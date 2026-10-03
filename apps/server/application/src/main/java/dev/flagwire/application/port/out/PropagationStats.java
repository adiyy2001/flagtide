package dev.flagwire.application.port.out;

import dev.flagwire.domain.value.EnvironmentRef;

public interface PropagationStats {

  void clientConnected(EnvironmentRef environment);

  void clientDisconnected(EnvironmentRef environment);

  void recordAcknowledgement(EnvironmentRef environment, long latencyMillis);

  PropagationReport report(EnvironmentRef environment);
}
