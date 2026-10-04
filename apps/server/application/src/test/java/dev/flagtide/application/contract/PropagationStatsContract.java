package dev.flagtide.application.contract;

import static dev.flagtide.application.testing.Samples.SHOP_DEV;
import static dev.flagtide.application.testing.Samples.SHOP_PROD;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagtide.application.port.out.PropagationReport;
import dev.flagtide.application.port.out.PropagationStats;
import java.time.Duration;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

public abstract class PropagationStatsContract extends AdapterContract {

  @Test
  void reportsNothingBeforeAnyAcknowledgement() {
    assertThat(this.adapters.propagationStats().report(SHOP_DEV))
        .isEqualTo(PropagationReport.empty(0));
  }

  @Test
  void countsConnectedClientsAndNeverGoesBelowZero() {
    PropagationStats stats = this.adapters.propagationStats();

    stats.clientConnected(SHOP_DEV);
    stats.clientConnected(SHOP_DEV);
    stats.clientConnected(SHOP_PROD);
    stats.clientDisconnected(SHOP_DEV);

    assertThat(stats.report(SHOP_DEV).connectedClients()).isEqualTo(1);
    assertThat(stats.report(SHOP_PROD).connectedClients()).isEqualTo(1);
    stats.clientDisconnected(SHOP_PROD);
    stats.clientDisconnected(SHOP_PROD);
    assertThat(stats.report(SHOP_PROD).connectedClients()).isZero();
  }

  @Test
  void computesPercentilesFromTheRecordedLatencies() {
    PropagationStats stats = this.adapters.propagationStats();
    LongStream.rangeClosed(1, 100)
        .forEach(latency -> stats.recordAcknowledgement(SHOP_DEV, latency));

    PropagationReport report = stats.report(SHOP_DEV);

    assertThat(report.samples()).isEqualTo(100);
    assertThat(report.p50Millis()).isEqualTo(50);
    assertThat(report.p95Millis()).isEqualTo(95);
    assertThat(report.p99Millis()).isEqualTo(99);
  }

  @Test
  void keepsEnvironmentsApart() {
    PropagationStats stats = this.adapters.propagationStats();
    stats.recordAcknowledgement(SHOP_DEV, 10);

    assertThat(stats.report(SHOP_PROD).samples()).isZero();
  }

  @Test
  void forgetsSamplesOlderThanSixtySeconds() {
    PropagationStats stats = this.adapters.propagationStats();
    stats.recordAcknowledgement(SHOP_DEV, 400);
    this.time.advance(Duration.ofSeconds(30));
    stats.recordAcknowledgement(SHOP_DEV, 20);

    assertThat(stats.report(SHOP_DEV).samples()).isEqualTo(2);

    this.time.advance(Duration.ofSeconds(40));

    PropagationReport later = stats.report(SHOP_DEV);
    assertThat(later.samples()).isEqualTo(1);
    assertThat(later.p95Millis()).isEqualTo(20);

    this.time.advance(Duration.ofSeconds(60));
    assertThat(stats.report(SHOP_DEV).samples()).isZero();
  }
}
