package dev.flagtide.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FlywayMigrationTest {

  private static final List<String> TABLES =
      List.of(
          "projects",
          "environment_versions",
          "flags",
          "segments",
          "api_keys",
          "change_log",
          "audit_log");

  @Inject Flyway flyway;
  @Inject DataSource dataSource;

  @Test
  void migratesACleanDatabaseAndValidates() {
    this.flyway.clean();

    int applied = this.flyway.migrate().migrationsExecuted;
    this.flyway.validate();

    assertThat(applied).isPositive();
    assertThat(this.flyway.info().pending()).isEmpty();
    assertThat(this.flyway.info().applied()).extracting(MigrationInfo::getVersion).isNotEmpty();
    assertThat(this.tablesInPublicSchema()).containsAll(TABLES);
  }

  @Test
  void migratingAgainChangesNothing() {
    this.flyway.migrate();

    assertThat(this.flyway.migrate().migrationsExecuted).isZero();
  }

  private List<String> tablesInPublicSchema() {
    return Jdbi.create(this.dataSource)
        .withHandle(
            handle ->
                handle
                    .createQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
                    .mapTo(String.class)
                    .list());
  }
}
