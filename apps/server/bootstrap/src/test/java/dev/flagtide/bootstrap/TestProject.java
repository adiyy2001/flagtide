package dev.flagtide.bootstrap;

import dev.flagtide.application.usecase.CreateProject;
import dev.flagtide.application.usecase.IssuedKey;
import dev.flagtide.domain.access.ApiKeyKind;
import dev.flagtide.domain.value.ProjectKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

record TestProject(String key, Map<String, String> admin, Map<String, String> sdk) {

  static TestProject create(CreateProject createProject) {
    String key = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    Map<String, String> admin = new HashMap<>();
    Map<String, String> sdk = new HashMap<>();
    for (IssuedKey issued :
        createProject
            .execute(new CreateProject.Command(new ProjectKey(key), "Test project", "test"))
            .value()
            .keys()) {
      Map<String, String> target = issued.key().kind() == ApiKeyKind.ADMIN ? admin : sdk;
      target.put(issued.key().environment().value(), issued.secret());
    }
    return new TestProject(key, Map.copyOf(admin), Map.copyOf(sdk));
  }

  String devAdmin() {
    return this.admin.get("dev");
  }

  String devSdk() {
    return this.sdk.get("dev");
  }

  String flagsPath() {
    return "/api/v1/projects/" + this.key + "/flags";
  }

  String segmentsPath(String environment) {
    return "/api/v1/projects/" + this.key + "/environments/" + environment + "/segments";
  }
}
