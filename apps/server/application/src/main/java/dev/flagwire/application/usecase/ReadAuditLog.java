package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.AuditLog;
import dev.flagwire.application.port.out.AuditQuery;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.audit.AuditEntry;
import dev.flagwire.domain.error.FlagwireException;
import java.util.List;

public final class ReadAuditLog {

  private static final int MAX_LIMIT = 500;

  private final AuditLog auditLog;
  private final Authorizer authorizer;

  public ReadAuditLog(AuditLog auditLog, Authorizer authorizer) {
    this.auditLog = auditLog;
    this.authorizer = authorizer;
  }

  public List<AuditEntry> execute(Principal principal, AuditQuery query) {
    this.authorizer.requireAdmin(principal);
    if (!query.project().equals(principal.project())) {
      throw FlagwireException.forbidden("the key belongs to another project");
    }
    if (query.limit() < 1 || query.limit() > MAX_LIMIT || query.offset() < 0) {
      throw FlagwireException.invalid("paging", "limit must be 1 to 500 and offset not negative");
    }
    return this.auditLog.find(query);
  }
}
