package dev.flagwire.adapter.postgres;

import dev.flagwire.application.contract.SegmentRepositoryContract;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.testing.TestAdapters;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sql.DataSource;

@QuarkusTest
class PostgresSegmentRepositoryContractTest extends SegmentRepositoryContract {

  @Inject DataSource dataSource;

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return PostgresTestAdapters.onEmptyDatabase(this.dataSource, changeLogRetention);
  }
}
