package dev.flagtide.adapter.out.postgres;

import dev.flagtide.application.port.out.TransactionRunner;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.transaction.Status;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.transaction.CMTTransactionHandler;

public final class PostgresDatabase implements TransactionRunner {

  private static final String READ_ONLY_SNAPSHOT =
      "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY";

  private final Jdbi jdbi;

  public PostgresDatabase(DataSource dataSource) {
    this.jdbi = Jdbi.create(dataSource).setTransactionHandler(new CMTTransactionHandler());
  }

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return QuarkusTransaction.joiningExisting().call(work::get);
  }

  @Override
  public <T> T inReadOnlyTransaction(Supplier<T> work) {
    if (QuarkusTransaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
      return work.get();
    }
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              this.jdbi.useHandle(handle -> handle.execute(READ_ONLY_SNAPSHOT));
              return work.get();
            });
  }

  <T> T query(Function<Handle, T> work) {
    return this.jdbi.withHandle(work::apply);
  }

  <T> T atomically(Function<Handle, T> work) {
    return this.inTransaction(() -> this.query(work));
  }
}
