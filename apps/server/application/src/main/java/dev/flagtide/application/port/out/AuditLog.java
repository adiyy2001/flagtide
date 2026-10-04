package dev.flagtide.application.port.out;

import dev.flagtide.domain.audit.AuditEntry;
import java.util.List;

public interface AuditLog {

  void append(AuditEntry entry);

  List<AuditEntry> find(AuditQuery query);
}
