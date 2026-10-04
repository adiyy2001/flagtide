package dev.flagtide.adapter.out.postgres;

import dev.flagtide.application.contract.ChangeFeedContract;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.testing.TestAdapters;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sql.DataSource;

@QuarkusTest
class PostgresChangeFeedContractTest extends ChangeFeedContract {

  @Inject DataSource dataSource;

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return PostgresTestAdapters.onEmptyDatabase(this.dataSource, changeLogRetention, timeSource);
  }
}
