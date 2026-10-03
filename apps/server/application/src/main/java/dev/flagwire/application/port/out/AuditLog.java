package dev.flagwire.application.port.out;

import dev.flagwire.domain.audit.AuditEntry;
import java.util.List;

public interface AuditLog {

  void append(AuditEntry entry);

  List<AuditEntry> find(AuditQuery query);
}
