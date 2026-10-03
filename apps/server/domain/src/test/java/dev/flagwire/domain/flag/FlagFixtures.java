package dev.flagwire.domain.flag;

import dev.flagwire.domain.evaluation.FlagType;
import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.event.Stamp;
import dev.flagwire.domain.event.Transition;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.Salt;
import dev.flagwire.domain.value.VariantKey;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public final class FlagFixtures {

  public static final EnvironmentKey DEV = new EnvironmentKey("dev");
  public static final EnvironmentKey STAGING = new EnvironmentKey("staging");
  public static final EnvironmentKey PROD = new EnvironmentKey("prod");
  public static final VariantKey ON = new VariantKey("on");
  public static final VariantKey OFF = new VariantKey("off");
  public static final ZonedDateTime NOW =
      ZonedDateTime.of(2026, 10, 3, 12, 0, 0, 0, ZoneOffset.UTC);

  private FlagFixtures() {}

  public static Stamp stamp(String author) {
    return new Stamp("event-1", NOW, author);
  }

  public static List<FlagVariant> booleanVariants() {
    return List.of(
        new FlagVariant(ON, new JsonValue.JsonBoolean(true)),
        new FlagVariant(OFF, new JsonValue.JsonBoolean(false)));
  }

  public static Map<EnvironmentKey, Salt> salts() {
    return Map.of(DEV, new Salt("a1"), STAGING, new Salt("b2"), PROD, new Salt("c3"));
  }

  public static Flag booleanFlag(String key) {
    return create(key, FlagType.BOOLEAN, booleanVariants(), OFF, OFF).next();
  }

  public static Transition<Flag> create(
      String key,
      FlagType type,
      List<FlagVariant> variants,
      VariantKey offVariant,
      VariantKey fallthrough) {
    return Flag.create(
        new FlagKey(key),
        "a flag",
        type,
        variants,
        offVariant,
        fallthrough,
        salts(),
        stamp("seed"));
  }

  public static EnvironmentSettings settings(boolean enabled, List<TargetingRule> rules) {
    return new EnvironmentSettings(enabled, OFF, rules, Serving.fixed(OFF));
  }
}
