package dev.flagwire.application.propagation;

import dev.flagwire.application.port.out.PropagationReport;
import dev.flagwire.application.port.out.PropagationStats;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentRef;
import java.time.Duration;
import java.time.ZonedDateTime;

public final class PropagationService {

  private final PropagationStats stats;
  private final Authorizer authorizer;

  public PropagationService(PropagationStats stats, Authorizer authorizer) {
    this.stats = stats;
    this.authorizer = authorizer;
  }

  public void clientConnected(Principal principal) {
    this.stats.clientConnected(principal.environmentRef());
  }

  public void clientDisconnected(Principal principal) {
    this.stats.clientDisconnected(principal.environmentRef());
  }

  public void recordAcknowledgement(
      Principal principal, ZonedDateTime committedAt, ZonedDateTime acknowledgedAt) {
    long latency = Duration.between(committedAt, acknowledgedAt).toMillis();
    this.stats.recordAcknowledgement(principal.environmentRef(), latency);
  }

  public PropagationReport report(Principal principal, EnvironmentKey environment) {
    this.authorizer.requireAdmin(principal);
    return this.stats.report(new EnvironmentRef(principal.project(), environment));
  }
}
