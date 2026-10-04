package dev.flagtide.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import dev.flagtide.adapter.in.rest.JsonNodes;
import dev.flagtide.domain.audit.AuditEntry;
import dev.flagtide.domain.evaluation.JsonValue;
import java.util.Locale;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AuditEntry")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEntryResponse(
    String id,
    String environment,
    Long environmentVersion,
    String entityType,
    String entityKey,
    String action,
    String author,
    String at,
    JsonNode before,
    JsonNode after) {

  public static AuditEntryResponse from(AuditEntry entry) {
    return new AuditEntryResponse(
        entry.id(),
        entry.environment().map(key -> key.value()).orElse(null),
        entry.environmentVersion().map(version -> version.value()).orElse(null),
        entry.entityType().name().toLowerCase(Locale.ROOT),
        entry.entityKey(),
        entry.action(),
        entry.author(),
        entry.at().toString(),
        entry.before().map(AuditEntryResponse::node).orElse(null),
        entry.after().map(AuditEntryResponse::node).orElse(null));
  }

  private static JsonNode node(JsonValue value) {
    return JsonNodes.toNode(value);
  }
}
