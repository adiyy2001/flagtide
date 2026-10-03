package dev.flagwire.domain.flag;

import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.FlagType;
import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.event.DomainEvent;
import dev.flagwire.domain.event.Stamp;
import dev.flagwire.domain.event.Transition;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.Revision;
import dev.flagwire.domain.value.Salt;
import dev.flagwire.domain.value.SegmentKey;
import dev.flagwire.domain.value.VariantKey;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record Flag(
    FlagKey key,
    String description,
    FlagType type,
    List<FlagVariant> variants,
    Map<EnvironmentKey, FlagEnvironmentConfig> environments,
    boolean archived,
    Revision revision,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt) {

  private static final int MAX_VARIANTS = 64;
  private static final int MAX_DESCRIPTION = 500;

  public Flag {
    variants = List.copyOf(variants);
    environments = Map.copyOf(environments);
    requireValidDescription(description);
    requireValidVariants(type, variants);
    requireEnvironments(environments);
    requireConfigsFitVariants(variants, environments);
    if (revision.compareTo(Revision.FIRST) < 0) {
      throw FlagwireException.invalid("revision", "a stored flag has a revision of at least 1");
    }
  }

  public static Transition<Flag> create(
      FlagKey key,
      String description,
      FlagType type,
      List<FlagVariant> variants,
      VariantKey offVariant,
      VariantKey fallthroughVariant,
      Map<EnvironmentKey, Salt> salts,
      Stamp stamp) {
    Map<EnvironmentKey, FlagEnvironmentConfig> configs =
        salts.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        new FlagEnvironmentConfig(
                            false,
                            false,
                            offVariant,
                            List.of(),
                            Serving.fixed(fallthroughVariant),
                            entry.getValue())));
    Flag created =
        new Flag(
            key,
            description,
            type,
            variants,
            configs,
            false,
            Revision.FIRST,
            stamp.occurredAt(),
            stamp.occurredAt());
    return Transition.of(created, new DomainEvent.FlagCreated(stamp, key, configs.keySet()));
  }

  public Optional<FlagEnvironmentConfig> environment(EnvironmentKey environment) {
    return Optional.ofNullable(this.environments.get(environment));
  }

  public FlagEnvironmentConfig requireEnvironment(EnvironmentKey environment) {
    return this.environment(environment)
        .orElseThrow(
            () ->
                FlagwireException.notFound(
                    "environment", environment.value() + " of flag " + this.key.value()));
  }

  public Optional<FlagVariant> variant(VariantKey variantKey) {
    return this.variants.stream().filter(variant -> variant.key().equals(variantKey)).findFirst();
  }

  public Transition<Flag> updateDefinition(
      String newDescription, List<FlagVariant> newVariants, Stamp stamp) {
    this.requireActive();
    Flag candidate =
        new Flag(
            this.key,
            newDescription,
            this.type,
            newVariants,
            this.environments,
            false,
            this.revision.next(),
            this.createdAt,
            stamp.occurredAt());
    boolean unchanged =
        this.description.equals(candidate.description) && this.variants.equals(candidate.variants);
    return unchanged
        ? Transition.unchanged(this)
        : Transition.of(
            candidate,
            new DomainEvent.FlagDefinitionChanged(stamp, this.key, this.environments.keySet()));
  }

  public Transition<Flag> configure(
      EnvironmentKey environment,
      EnvironmentSettings settings,
      Set<SegmentKey> knownSegments,
      Stamp stamp) {
    this.requireActive();
    FlagEnvironmentConfig current = this.requireEnvironment(environment);
    FlagEnvironmentConfig replacement =
        new FlagEnvironmentConfig(
            settings.enabled(),
            current.killSwitch(),
            settings.offVariant(),
            settings.rules(),
            settings.fallthrough(),
            current.salt());
    requireSegmentsExist(replacement, knownSegments);
    return this.replaceEnvironment(
        environment,
        replacement,
        stamp,
        new DomainEvent.FlagConfigChanged(stamp, this.key, environment));
  }

  public Transition<Flag> toggle(EnvironmentKey environment, boolean enabled, Stamp stamp) {
    this.requireActive();
    FlagEnvironmentConfig current = this.requireEnvironment(environment);
    return this.replaceEnvironment(
        environment,
        current.withEnabled(enabled),
        stamp,
        new DomainEvent.FlagToggled(stamp, this.key, environment, enabled));
  }

  public Transition<Flag> engageKillSwitch(EnvironmentKey environment, Stamp stamp) {
    this.requireActive();
    FlagEnvironmentConfig current = this.requireEnvironment(environment);
    return this.replaceEnvironment(
        environment,
        current.withKillSwitch(true),
        stamp,
        new DomainEvent.KillSwitchEngaged(stamp, this.key, environment));
  }

  public Transition<Flag> releaseKillSwitch(EnvironmentKey environment, Stamp stamp) {
    this.requireActive();
    FlagEnvironmentConfig current = this.requireEnvironment(environment);
    return this.replaceEnvironment(
        environment,
        current.withKillSwitch(false),
        stamp,
        new DomainEvent.KillSwitchReleased(stamp, this.key, environment));
  }

  public Transition<Flag> archive(Stamp stamp) {
    this.requireActive();
    Flag archivedFlag =
        new Flag(
            this.key,
            this.description,
            this.type,
            this.variants,
            this.environments,
            true,
            this.revision.next(),
            this.createdAt,
            stamp.occurredAt());
    return Transition.of(
        archivedFlag, new DomainEvent.FlagArchived(stamp, this.key, this.environments.keySet()));
  }

  public Flag withEnvironment(EnvironmentKey environment, Salt salt, ZonedDateTime at) {
    if (this.environments.containsKey(environment)) {
      throw FlagwireException.conflict(
          "flag " + this.key.value() + " already has environment " + environment.value());
    }
    VariantKey off =
        this.environments.entrySet().stream()
            .min(Map.Entry.comparingByKey())
            .map(entry -> entry.getValue().offVariant())
            .orElseThrow();
    Map<EnvironmentKey, FlagEnvironmentConfig> extended = new HashMap<>(this.environments);
    extended.put(environment, FlagEnvironmentConfig.initial(off, salt));
    return new Flag(
        this.key,
        this.description,
        this.type,
        this.variants,
        extended,
        this.archived,
        this.revision.next(),
        this.createdAt,
        at);
  }

  public Set<SegmentKey> referencedSegments(EnvironmentKey environment) {
    return this.environment(environment)
        .map(FlagEnvironmentConfig::referencedSegments)
        .orElse(Set.of());
  }

  private Transition<Flag> replaceEnvironment(
      EnvironmentKey environment,
      FlagEnvironmentConfig replacement,
      Stamp stamp,
      DomainEvent event) {
    if (replacement.equals(this.environments.get(environment))) {
      return Transition.unchanged(this);
    }
    Map<EnvironmentKey, FlagEnvironmentConfig> updated = new HashMap<>(this.environments);
    updated.put(environment, replacement);
    Flag next =
        new Flag(
            this.key,
            this.description,
            this.type,
            this.variants,
            updated,
            this.archived,
            this.revision.next(),
            this.createdAt,
            stamp.occurredAt());
    return Transition.of(next, event);
  }

  private void requireActive() {
    if (this.archived) {
      throw FlagwireException.conflict("flag " + this.key.value() + " is archived");
    }
  }

  private static void requireSegmentsExist(
      FlagEnvironmentConfig config, Set<SegmentKey> knownSegments) {
    Set<SegmentKey> missing = new HashSet<>(config.referencedSegments());
    missing.removeAll(knownSegments);
    if (!missing.isEmpty()) {
      throw FlagwireException.invalid(
          "rules",
          "unknown segments: "
              + missing.stream().map(SegmentKey::value).sorted().collect(Collectors.joining(", ")));
    }
  }

  private static void requireValidDescription(String description) {
    if (description.length() > MAX_DESCRIPTION) {
      throw FlagwireException.invalid("description", "must be at most 500 characters");
    }
  }

  private static void requireValidVariants(FlagType type, List<FlagVariant> variants) {
    if (variants.isEmpty() || variants.size() > MAX_VARIANTS) {
      throw FlagwireException.invalid("variants", "a flag needs 1 to 64 variants");
    }
    Set<VariantKey> keys = new HashSet<>();
    variants.forEach(
        variant -> {
          if (!keys.add(variant.key())) {
            throw FlagwireException.invalid(
                "variants", "variant key " + variant.key().value() + " is used twice");
          }
          VariantValues.requireFits(type, variant);
        });
    if (type == FlagType.BOOLEAN) {
      requireDistinctBooleans(variants);
    }
  }

  private static void requireDistinctBooleans(List<FlagVariant> variants) {
    Set<JsonValue> values = variants.stream().map(FlagVariant::value).collect(Collectors.toSet());
    if (values.size() != variants.size()) {
      throw FlagwireException.invalid("variants", "a boolean flag has one variant per value");
    }
  }

  private static void requireEnvironments(Map<EnvironmentKey, FlagEnvironmentConfig> environments) {
    if (environments.isEmpty()) {
      throw FlagwireException.invalid("environments", "a flag needs at least one environment");
    }
  }

  private static void requireConfigsFitVariants(
      List<FlagVariant> variants, Map<EnvironmentKey, FlagEnvironmentConfig> environments) {
    Set<VariantKey> known = variants.stream().map(FlagVariant::key).collect(Collectors.toSet());
    environments.forEach(
        (environment, config) ->
            config.servedVariants().stream()
                .filter(served -> !known.contains(served))
                .findFirst()
                .ifPresent(
                    missing -> {
                      throw FlagwireException.invalid(
                          "environments." + environment.value(),
                          "variant " + missing.value() + " does not exist");
                    }));
  }
}
