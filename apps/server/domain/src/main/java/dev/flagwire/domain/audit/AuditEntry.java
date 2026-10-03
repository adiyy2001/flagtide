package dev.flagwire.domain.audit;

import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentVersion;
import dev.flagwire.domain.value.ProjectKey;
import java.time.ZonedDateTime;
import java.util.Optional;

public record AuditEntry(
    String id,
    ProjectKey project,
    Optional<EnvironmentKey> environment,
    Optional<EnvironmentVersion> environmentVersion,
    EntityType entityType,
    String entityKey,
    String action,
    String author,
    ZonedDateTime at,
    Optional<JsonValue> before,
    Optional<JsonValue> after) {}
