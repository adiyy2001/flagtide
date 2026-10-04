package dev.flagtide.domain.evaluation;

import java.util.Optional;
import java.util.OptionalInt;

public record EvaluationResult(
    String variantKey,
    JsonValue value,
    Reason reason,
    OptionalInt ruleIndex,
    Optional<String> ruleId,
    OptionalInt bucket) {}
