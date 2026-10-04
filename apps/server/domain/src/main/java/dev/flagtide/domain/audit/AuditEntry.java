package dev.flagtide.domain.audit;

import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentVersion;
import dev.flagtide.domain.value.ProjectKey;
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
