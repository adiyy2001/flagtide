package dev.flagwire.adapter.out.postgres;

import dev.flagwire.application.contract.ChangeLogContract;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.testing.TestAdapters;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sql.DataSource;

@QuarkusTest
class PostgresChangeLogContractTest extends ChangeLogContract {

  @Inject DataSource dataSource;

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return PostgresTestAdapters.onEmptyDatabase(this.dataSource, changeLogRetention);
  }
}
