package dev.flagwire.adapter.out.postgres;

import dev.flagwire.application.port.out.PropagationReport;
import dev.flagwire.application.port.out.PropagationStats;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.propagation.LatencyHistogram;
import dev.flagwire.application.propagation.LocalPropagationStats;
import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.json.Json;
import dev.flagwire.domain.json.JsonFields;
import dev.flagwire.domain.json.JsonText;
import dev.flagwire.domain.value.EnvironmentRef;
import java.util.List;
import java.util.Map;
import org.jdbi.v3.core.Handle;

public final class PostgresPropagationStats implements PropagationStats {

  public static final int FRESH_SECONDS = 5;
  public static final int FORGET_SECONDS = 60;

  private record InstanceRow(int connected, LatencyHistogram latencies) {}

  private static final String UPSERT =
      """
      INSERT INTO instance_stats
        (instance_id, project_key, environment_key, updated_at, connected, buckets)
      VALUES (:instance, :project, :environment, now(), :connected, %s)
      ON CONFLICT (instance_id, project_key, environment_key)
      DO UPDATE SET updated_at = now(), connected = EXCLUDED.connected, buckets = EXCLUDED.buckets
      """
          .formatted(Documents.jsonb("buckets"));

  private static final String FORGET_STALE =
      "DELETE FROM instance_stats WHERE updated_at < now() - make_interval(secs => :seconds)";

  private static final String WITHDRAW = "DELETE FROM instance_stats WHERE instance_id = :instance";

  private static final String OTHER_INSTANCES =
      """
      SELECT connected, buckets FROM instance_stats
      WHERE project_key = :project AND environment_key = :environment
        AND instance_id <> :instance
        AND updated_at > now() - make_interval(secs => :seconds)
      """;

  private final PostgresDatabase database;
  private final String instanceId;
  private final LocalPropagationStats local;

  public PostgresPropagationStats(
      PostgresDatabase database, TimeSource timeSource, String instanceId) {
    this.database = database;
    this.instanceId = instanceId;
    this.local = new LocalPropagationStats(timeSource);
  }

  @Override
  public void clientConnected(EnvironmentRef environment) {
    this.local.clientConnected(environment);
  }

  @Override
  public void clientDisconnected(EnvironmentRef environment) {
    this.local.clientDisconnected(environment);
  }

  @Override
  public void recordAcknowledgement(EnvironmentRef environment, long latencyMillis) {
    this.local.recordAcknowledgement(environment, latencyMillis);
  }

  @Override
  public PropagationReport report(EnvironmentRef environment) {
    LatencyHistogram merged = this.local.window(environment);
    int connected = this.local.connectedClients(environment);
    for (InstanceRow row : this.otherInstances(environment)) {
      merged.merge(row.latencies());
      connected += row.connected();
    }
    return merged.report(connected);
  }

  @Override
  public void publish() {
    this.database.query(
        handle -> {
          this.local.environments().forEach(environment -> this.upsert(handle, environment));
          handle.createUpdate(FORGET_STALE).bind("seconds", (double) FORGET_SECONDS).execute();
          return null;
        });
  }

  @Override
  public void withdraw() {
    this.database.query(
        handle -> handle.createUpdate(WITHDRAW).bind("instance", this.instanceId).execute());
  }

  private void upsert(Handle handle, EnvironmentRef environment) {
    handle
        .createUpdate(UPSERT)
        .bind("instance", this.instanceId)
        .bind("project", environment.project().value())
        .bind("environment", environment.environment().value())
        .bind("connected", this.local.connectedClients(environment))
        .bind("buckets", JsonText.write(bucketsToJson(this.local.window(environment))))
        .execute();
  }

  private List<InstanceRow> otherInstances(EnvironmentRef environment) {
    return this.database.query(
        handle ->
            handle
                .createQuery(OTHER_INSTANCES)
                .bind("project", environment.project().value())
                .bind("environment", environment.environment().value())
                .bind("instance", this.instanceId)
                .bind("seconds", (double) FRESH_SECONDS)
                .map(
                    (rows, context) ->
                        new InstanceRow(
                            rows.getInt("connected"),
                            bucketsFromJson(Documents.document(rows, "buckets"))))
                .list());
  }

  static JsonValue bucketsToJson(LatencyHistogram histogram) {
    Json.ObjectBuilder buckets = Json.object();
    histogram.sparse().forEach((bucket, count) -> buckets.number(Integer.toString(bucket), count));
    return buckets.build();
  }

  static LatencyHistogram bucketsFromJson(JsonValue json) {
    LatencyHistogram histogram = new LatencyHistogram();
    Map<String, JsonValue> members = JsonFields.of("buckets", json).members();
    members.forEach(
        (bucket, count) ->
            histogram.add(Integer.parseInt(bucket), (long) ((JsonValue.JsonNumber) count).value()));
    return histogram;
  }
}
