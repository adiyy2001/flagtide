package dev.flagtide.application.testing;

import dev.flagtide.application.change.Change;
import dev.flagtide.domain.access.ApiKey;
import dev.flagtide.domain.access.ApiKeyKind;
import dev.flagtide.domain.audit.AuditEntry;
import dev.flagtide.domain.audit.EntityType;
import dev.flagtide.domain.evaluation.FlagType;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.event.Stamp;
import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.flag.FlagVariant;
import dev.flagtide.domain.project.Environment;
import dev.flagtide.domain.project.Project;
import dev.flagtide.domain.segment.Segment;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.EnvironmentVersion;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.ProjectKey;
import dev.flagtide.domain.value.Salt;
import dev.flagtide.domain.value.SegmentKey;
import dev.flagtide.domain.value.VariantKey;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Samples {

  public static final ZonedDateTime NOW =
      ZonedDateTime.of(2026, 10, 3, 12, 0, 0, 0, ZoneOffset.UTC);
  public static final ProjectKey SHOP = new ProjectKey("shop");
  public static final ProjectKey BLOG = new ProjectKey("blog");
  public static final EnvironmentKey DEV = new EnvironmentKey("dev");
  public static final EnvironmentKey PROD = new EnvironmentKey("prod");
  public static final VariantKey ON = new VariantKey("on");
  public static final VariantKey OFF = new VariantKey("off");
  public static final EnvironmentRef SHOP_DEV = new EnvironmentRef(SHOP, DEV);
  public static final EnvironmentRef SHOP_PROD = new EnvironmentRef(SHOP, PROD);
  public static final EnvironmentRef BLOG_DEV = new EnvironmentRef(BLOG, DEV);

  private Samples() {}

  public static Stamp stamp(String author) {
    return new Stamp("event-" + author, NOW, author);
  }

  public static List<FlagVariant> booleanVariants() {
    return List.of(
        new FlagVariant(ON, new JsonValue.JsonBoolean(true)),
        new FlagVariant(OFF, new JsonValue.JsonBoolean(false)));
  }

  public static Flag flag(String key) {
    return Flag.create(
            new FlagKey(key),
            "sample",
            FlagType.BOOLEAN,
            booleanVariants(),
            OFF,
            OFF,
            Map.of(DEV, new Salt("a1"), PROD, new Salt("b2")),
            stamp("sample"))
        .next();
  }

  public static Flag nextRevision(Flag flag) {
    return flag.toggle(DEV, !flag.requireEnvironment(DEV).enabled(), stamp("sample")).next();
  }

  public static Segment segment(String key) {
    return Segment.create(
            DEV,
            new SegmentKey(key),
            "Segment " + key,
            Set.of("u1"),
            Set.of(),
            List.of(),
            stamp("sample"))
        .next();
  }

  public static Project project(String key) {
    return Project.create(
            new ProjectKey(key),
            "Project " + key,
            List.of(new Environment(DEV, "Development"), new Environment(PROD, "Production")),
            stamp("sample"))
        .next();
  }

  public static ApiKey apiKey(String id, ApiKeyKind kind, ProjectKey project, EnvironmentKey env) {
    return new ApiKey(id, kind, project, env, "label-" + id, "lookup-" + id, NOW);
  }

  public static AuditEntry audit(String id, ProjectKey project, EnvironmentKey env, String entity) {
    return new AuditEntry(
        id,
        project,
        Optional.ofNullable(env),
        Optional.of(EnvironmentVersion.of(1)),
        EntityType.FLAG,
        entity,
        "FlagToggled",
        "ann",
        NOW,
        Optional.of(new JsonValue.JsonBoolean(false)),
        Optional.of(new JsonValue.JsonBoolean(true)));
  }

  public static List<Change> upsert(String key) {
    return List.of(new Change.FlagRemoved(key));
  }
}
