package dev.flagwire.application.propagation;

import static dev.flagwire.application.testing.Samples.DEV;
import static dev.flagwire.application.testing.Samples.SHOP;
import static dev.flagwire.application.testing.Samples.SHOP_DEV;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.application.port.out.PropagationReport;
import dev.flagwire.application.port.out.PropagationStats;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.application.testing.Samples;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.EnvironmentRef;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PropagationServiceTest {

  private final List<String> calls = new ArrayList<>();
  private final PropagationStats stats =
      new PropagationStats() {
        @Override
        public void clientConnected(EnvironmentRef environment) {
          PropagationServiceTest.this.calls.add("connected " + environment.environment().value());
        }

        @Override
        public void clientDisconnected(EnvironmentRef environment) {
          PropagationServiceTest.this.calls.add(
              "disconnected " + environment.environment().value());
        }

        @Override
        public void recordAcknowledgement(EnvironmentRef environment, long latencyMillis) {
          PropagationServiceTest.this.calls.add("latency " + latencyMillis);
        }

        @Override
        public PropagationReport report(EnvironmentRef environment) {
          return new PropagationReport(2, 3, 4, 5, 6);
        }
      };
  private final PropagationService service = new PropagationService(this.stats, new Authorizer());
  private final Principal sdk = Principal.of(Samples.apiKey("s", ApiKeyKind.SDK, SHOP, DEV));
  private final Principal admin = Principal.of(Samples.apiKey("a", ApiKeyKind.ADMIN, SHOP, DEV));

  @Test
  void tracksConnectionsForTheEnvironmentOfTheKey() {
    this.service.clientConnected(this.sdk);
    this.service.clientDisconnected(this.sdk);

    assertThat(this.calls).containsExactly("connected dev", "disconnected dev");
  }

  @Test
  void recordsTheLatencyBetweenCommitAndAcknowledgement() {
    this.service.recordAcknowledgement(
        this.sdk, Samples.NOW, Samples.NOW.plus(Duration.ofMillis(137)));

    assertThat(this.calls).containsExactly("latency 137");
  }

  @Test
  void reportsOnlyToAdminKeys() {
    assertThat(this.service.report(this.admin, DEV))
        .isEqualTo(new PropagationReport(2, 3, 4, 5, 6));
    assertThatThrownBy(() -> this.service.report(this.sdk, DEV))
        .isInstanceOf(FlagwireException.class);
    assertThat(SHOP_DEV.environment()).isEqualTo(DEV);
  }
}
