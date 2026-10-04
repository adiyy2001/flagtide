package dev.flagtide.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.pgclient.PgConnectOptions;
import org.junit.jupiter.api.Test;

class ListenerConnectionTest {

  @Test
  void readsHostPortAndDatabaseFromTheJdbcUrl() {
    PgConnectOptions options =
        ListenerConnection.optionsFromJdbcUrl(
            "jdbc:postgresql://db.internal:15432/flagtide?sslmode=disable", "u", "p", "listener");

    assertThat(options.getHost()).isEqualTo("db.internal");
    assertThat(options.getPort()).isEqualTo(15432);
    assertThat(options.getDatabase()).isEqualTo("flagtide");
    assertThat(options.getUser()).isEqualTo("u");
    assertThat(options.getPassword()).isEqualTo("p");
    assertThat(options.getProperties()).containsEntry("application_name", "listener");
  }

  @Test
  void usesThePostgresPortWhenTheUrlHasNone() {
    PgConnectOptions options =
        ListenerConnection.optionsFromJdbcUrl(
            "jdbc:postgresql://localhost/flagtide", "u", "p", "l");

    assertThat(options.getPort()).isEqualTo(5432);
  }
}
