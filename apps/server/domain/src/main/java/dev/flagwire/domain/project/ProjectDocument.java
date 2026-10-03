package dev.flagwire.domain.project;

import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.json.Json;
import dev.flagwire.domain.json.JsonFields;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.ProjectKey;
import dev.flagwire.domain.value.Revision;
import java.time.ZonedDateTime;

public final class ProjectDocument {

  private ProjectDocument() {}

  public static JsonValue toJson(Project project) {
    return Json.object()
        .text("key", project.key().value())
        .text("name", project.name())
        .number("revision", project.revision().value())
        .text("createdAt", project.createdAt().toString())
        .put("environments", Json.array(project.environments(), ProjectDocument::environmentToJson))
        .build();
  }

  public static Project fromJson(JsonValue json) {
    JsonFields fields = JsonFields.of("project", json);
    return new Project(
        new ProjectKey(fields.text("key")),
        fields.text("name"),
        fields.array("environments").stream().map(ProjectDocument::environmentFromJson).toList(),
        new Revision(fields.integer("revision")),
        ZonedDateTime.parse(fields.text("createdAt")));
  }

  private static JsonValue environmentToJson(Environment environment) {
    return Json.object()
        .text("key", environment.key().value())
        .text("name", environment.name())
        .build();
  }

  private static Environment environmentFromJson(JsonValue json) {
    JsonFields fields = JsonFields.of("environment", json);
    return new Environment(new EnvironmentKey(fields.text("key")), fields.text("name"));
  }
}
