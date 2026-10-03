package dev.flagwire.adapter.out.postgres;

import static dev.flagwire.application.testing.Samples.NOW;
import static dev.flagwire.application.testing.Samples.SHOP_DEV;
import static dev.flagwire.application.testing.Samples.SHOP_PROD;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagwire.application.port.out.PropagationReport;
import dev.flagwire.application.propagation.LatencyHistogram;
import dev.flagwire.application.testing.MutableTimeSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InstanceStatsTest {

  @Inject DataSource dataSource;

  private MutableTimeSource time;
  private PostgresPropagationStats instanceA;
  private PostgresPropagationStats instanceB;

  @BeforeEach
  void startTwoInstances() {
    this.time = new MutableTimeSource(NOW);
    this.instanceA =
        PostgresTestAdapters.onEmptyDatabase(this.dataSource, 100, this.time, "a").stats();
    PostgresAdapters shared = PostgresAdapters.create(this.dataSource, 100);
    this.instanceB = new PostgresPropagationStats(shared.transactions(), this.time, "b");
  }

  @Test
  void aReportMergesClientsAndLatenciesOfEveryInstance() {
    this.instanceA.clientConnected(SHOP_DEV);
    this.instanceA.clientConnected(SHOP_DEV);
    LongStream.rangeClosed(1, 50)
        .forEach(latency -> this.instanceA.recordAcknowledgement(SHOP_DEV, latency));
    this.instanceB.clientConnected(SHOP_DEV);
    LongStream.rangeClosed(51, 100)
        .forEach(latency -> this.instanceB.recordAcknowledgement(SHOP_DEV, latency));
    this.instanceA.publish();
    this.instanceB.publish();

    PropagationReport seenFromA = this.instanceA.report(SHOP_DEV);
    PropagationReport seenFromB = this.instanceB.report(SHOP_DEV);

    assertThat(seenFromA).isEqualTo(seenFromB);
    assertThat(seenFromA.connectedClients()).isEqualTo(3);
    assertThat(seenFromA.samples()).isEqualTo(100);
    assertThat(seenFromA.p50Millis()).isEqualTo(50);
    assertThat(seenFromA.p95Millis()).isEqualTo(95);
    assertThat(seenFromA.p99Millis()).isEqualTo(99);
  }

  @Test
  void anInstanceSeesItsOwnLiveNumbersBeforeItPublishes() {
    this.instanceA.clientConnected(SHOP_DEV);
    this.instanceA.recordAcknowledgement(SHOP_DEV, 7);

    PropagationReport report = this.instanceA.report(SHOP_DEV);

    assertThat(report.connectedClients()).isEqualTo(1);
    assertThat(report.p50Millis()).isEqualTo(7);
  }

  @Test
  void environmentsStayApart() {
    this.instanceA.clientConnected(SHOP_DEV);
    this.instanceA.publish();

    assertThat(this.instanceB.report(SHOP_PROD)).isEqualTo(PropagationReport.empty(0));
  }

  @Test
  void anInstanceThatStoppedPublishingDropsOutAfterFiveSeconds() {
    this.instanceA.clientConnected(SHOP_DEV);
    this.instanceA.publish();
    Jdbi.create(this.dataSource)
        .useHandle(
            handle ->
                handle.execute(
                    "UPDATE instance_stats SET updated_at = now() - interval '6 seconds'"));

    assertThat(this.instanceB.report(SHOP_DEV).connectedClients()).isZero();
  }

  @Test
  void publishingForgetsRowsOlderThanAMinute() {
    this.instanceA.clientConnected(SHOP_DEV);
    this.instanceA.publish();
    Jdbi.create(this.dataSource)
        .useHandle(
            handle ->
                handle.execute(
                    "UPDATE instance_stats SET updated_at = now() - interval '2 minutes'"));

    this.instanceB.clientConnected(SHOP_PROD);
    this.instanceB.publish();

    long rows =
        Jdbi.create(this.dataSource)
            .withHandle(
                handle ->
                    handle
                        .createQuery("SELECT count(*) FROM instance_stats WHERE instance_id = 'a'")
                        .mapTo(Long.class)
                        .one());
    assertThat(rows).isZero();
  }

  @Test
  void withdrawingRemovesTheRowsOfThatInstanceOnly() {
    this.instanceA.clientConnected(SHOP_DEV);
    this.instanceB.clientConnected(SHOP_DEV);
    this.instanceA.publish();
    this.instanceB.publish();

    this.instanceA.withdraw();

    assertThat(this.instanceB.report(SHOP_DEV).connectedClients()).isEqualTo(1);
    PostgresAdapters other = PostgresAdapters.create(this.dataSource, 100);
    PostgresPropagationStats observer =
        new PostgresPropagationStats(other.transactions(), this.time, "observer");
    assertThat(observer.report(SHOP_DEV).connectedClients()).isEqualTo(1);
  }

  @Test
  void bucketsSurviveTheRoundTripThroughJson() {
    LatencyHistogram histogram = new LatencyHistogram();
    histogram.record(3);
    histogram.record(3);
    histogram.record(9000);

    LatencyHistogram restored =
        PostgresPropagationStats.bucketsFromJson(PostgresPropagationStats.bucketsToJson(histogram));

    assertThat(restored.sparse()).isEqualTo(histogram.sparse());
  }
}
