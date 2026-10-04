package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.AuditQuery;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.audit.AuditEntry;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.value.EnvironmentKey;
import java.util.List;
import java.util.Optional;

public final class ReadAuditLog {

  private static final int MAX_LIMIT = 500;

  public record Query(
      Optional<EnvironmentKey> environment, Optional<String> entityKey, int limit, int offset) {

    public static Query newest(int limit) {
      return new Query(Optional.empty(), Optional.empty(), limit, 0);
    }

    public Query inEnvironment(EnvironmentKey key) {
      return new Query(Optional.of(key), this.entityKey, this.limit, this.offset);
    }

    public Query aboutEntity(String key) {
      return new Query(this.environment, Optional.of(key), this.limit, this.offset);
    }

    public Query skipping(int skipped) {
      return new Query(this.environment, this.entityKey, this.limit, skipped);
    }
  }

  private final AuditLog auditLog;
  private final Authorizer authorizer;

  public ReadAuditLog(AuditLog auditLog, Authorizer authorizer) {
    this.auditLog = auditLog;
    this.authorizer = authorizer;
  }

  public List<AuditEntry> execute(Principal principal, Query query) {
    this.authorizer.requireAdmin(principal);
    if (query.limit() < 1 || query.limit() > MAX_LIMIT || query.offset() < 0) {
      throw FlagtideException.invalid("paging", "limit must be 1 to 500 and offset not negative");
    }
    return this.auditLog.find(
        new AuditQuery(
            principal.project(),
            query.environment(),
            query.entityKey(),
            query.limit(),
            query.offset()));
  }
}
