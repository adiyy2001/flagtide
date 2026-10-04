package dev.flagtide.bootstrap;

import static dev.flagtide.bootstrap.Requests.anonymous;
import static dev.flagtide.bootstrap.Requests.as;
import static dev.flagtide.bootstrap.Requests.booleanFlag;
import static dev.flagtide.bootstrap.Requests.settings;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import dev.flagtide.application.usecase.CreateProject;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class AdminApiTest {

  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String ROLLOUT_60_40 =
      """
      {"rollout":[{"variant":"on","weight":60000},{"variant":"off","weight":40000}]}
      """;
  private static final String ON = "{\"variant\":\"on\"}";
  private static final String PLAN_RULE =
      """
      [{"id":"beta","order":0,
        "conditions":[{"attribute":"plan","operator":"in","values":["pro","team"]}],
        "serve":{"variant":"on"}}]
      """;

  @Inject CreateProject createProject;

  TestProject project;

  @BeforeEach
  void freshProject() {
    this.project = TestProject.create(this.createProject);
  }

  private String etagOfCreatedFlag(String key) {
    return as(this.project.devAdmin())
        .body(booleanFlag(key))
        .post(this.project.flagsPath())
        .then()
        .statusCode(201)
        .extract()
        .header("ETag");
  }

  @Test
  void aRequestWithoutKeyIsRejectedWithProblemDetails() {
    anonymous()
        .get(this.project.flagsPath())
        .then()
        .statusCode(401)
        .contentType(PROBLEM_JSON)
        .header("WWW-Authenticate", equalTo("Bearer"))
        .body("status", equalTo(401))
        .body("type", equalTo("urn:flagtide:problem:unauthorized"));
  }

  @Test
  void anUnknownKeyIsRejected() {
    as("fwa_not_a_real_key_at_all")
        .get(this.project.flagsPath())
        .then()
        .statusCode(401)
        .contentType(PROBLEM_JSON);
  }

  @Test
  void aKeyWithoutBearerSchemeIsRejected() {
    given()
        .header("Authorization", this.project.devAdmin())
        .get(this.project.flagsPath())
        .then()
        .statusCode(401);
  }

  @Test
  void anSdkKeyCannotUseTheAdminApi() {
    as(this.project.devSdk()).get(this.project.flagsPath()).then().statusCode(403);
  }

  @Test
  void aKeyOfAnotherProjectIsForbidden() {
    TestProject other = TestProject.create(this.createProject);

    as(other.devAdmin()).get(this.project.flagsPath()).then().statusCode(403);
  }

  @Test
  void anAdminKeyOfAnotherEnvironmentCannotChangeTheEnvironment() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.admin().get("staging"))
        .body("{\"enabled\":true}")
        .put(this.project.flagsPath() + "/checkout/environments/dev/enabled")
        .then()
        .statusCode(403)
        .contentType(PROBLEM_JSON);
  }

  @Test
  void healthAndOpenApiNeedNoKey() {
    given().get("/q/health/live").then().statusCode(200);
    given().get("/q/health/ready").then().statusCode(200);
    given().get("/q/openapi?format=json").then().statusCode(200).body("openapi", notNullValue());
  }

  @Test
  void corsAnswersAllowedOriginsOnly() {
    given()
        .header("Origin", "http://localhost:14200")
        .header("Access-Control-Request-Method", "PUT")
        .header("Access-Control-Request-Headers", "authorization,if-match")
        .options(this.project.flagsPath())
        .then()
        .statusCode(200)
        .header("Access-Control-Allow-Origin", equalTo("http://localhost:14200"));
    given()
        .header("Origin", "http://evil.example")
        .header("Access-Control-Request-Method", "PUT")
        .options(this.project.flagsPath())
        .then()
        .header("Access-Control-Allow-Origin", nullValue());
  }

  @Test
  void theProjectListsItsDefaultEnvironments() {
    as(this.project.devAdmin())
        .get("/api/v1/projects/" + this.project.key())
        .then()
        .statusCode(200)
        .body("key", equalTo(this.project.key()))
        .body("environments.key", equalTo(java.util.List.of("dev", "staging", "prod")));
  }

  @Test
  void aNewEnvironmentComesWithKeysThatSeeTheExistingFlags() {
    this.etagOfCreatedFlag("checkout");

    String sdkKey =
        as(this.project.devAdmin())
            .body("{\"key\":\"qa\",\"name\":\"QA\"}")
            .post("/api/v1/projects/" + this.project.key() + "/environments")
            .then()
            .statusCode(201)
            .body("keys", hasSize(2))
            .body("keys.kind", hasItem("sdk"))
            .extract()
            .path("keys.find { it.kind == 'sdk' }.secret");

    as(sdkKey)
        .get("/sdk/v1/snapshot")
        .then()
        .statusCode(200)
        .body("flags.key", equalTo(java.util.List.of("checkout")));
  }

  @Test
  void anExistingEnvironmentCannotBeCreatedAgain() {
    as(this.project.devAdmin())
        .body("{\"key\":\"prod\",\"name\":\"Again\"}")
        .post("/api/v1/projects/" + this.project.key() + "/environments")
        .then()
        .statusCode(409)
        .contentType(PROBLEM_JSON);
  }

  @Test
  void keysOfAnEnvironmentAreListedWithoutAdminSecrets() {
    as(this.project.devAdmin())
        .get("/api/v1/projects/" + this.project.key() + "/environments/dev/keys")
        .then()
        .statusCode(200)
        .body("kind", equalTo(java.util.List.of("admin", "sdk")))
        .body("find { it.kind == 'sdk' }.sdkKey", equalTo(this.project.devSdk()))
        .body("find { it.kind == 'admin' }.sdkKey", nullValue());
  }

  @Test
  void creatingAFlagReturnsItsLocationAndFirstRevision() {
    as(this.project.devAdmin())
        .body(booleanFlag("checkout"))
        .post(this.project.flagsPath())
        .then()
        .statusCode(201)
        .header("ETag", equalTo("\"1\""))
        .header("Location", containsString(this.project.flagsPath() + "/checkout"))
        .body("key", equalTo("checkout"))
        .body("type", equalTo("boolean"))
        .body("revision", equalTo(1))
        .body("environments.dev.enabled", equalTo(false))
        .body("environments.prod.offVariant", equalTo("off"))
        .body("environments.dev.salt", notNullValue());
  }

  @Test
  void aFlagIsReadBackWithItsETag() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .get(this.project.flagsPath() + "/checkout")
        .then()
        .statusCode(200)
        .header("ETag", equalTo("\"1\""))
        .body("description", equalTo("checkout redesign"));
  }

  @Test
  void anUnknownFlagIsNotFound() {
    as(this.project.devAdmin())
        .get(this.project.flagsPath() + "/missing")
        .then()
        .statusCode(404)
        .contentType(PROBLEM_JSON)
        .body("detail", containsString("missing"));
  }

  @Test
  void creatingTheSameFlagTwiceConflicts() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .body(booleanFlag("checkout"))
        .post(this.project.flagsPath())
        .then()
        .statusCode(409)
        .contentType(PROBLEM_JSON);
  }

  @Test
  void invalidInputIsReportedPerField() {
    as(this.project.devAdmin())
        .body(booleanFlag("Not A Slug"))
        .post(this.project.flagsPath())
        .then()
        .statusCode(400)
        .contentType(PROBLEM_JSON)
        .body("title", equalTo("Validation failed"))
        .body("errors[0].field", equalTo("flag"));
  }

  @Test
  void aMissingRequiredFieldIsNamed() {
    as(this.project.devAdmin())
        .body("{\"key\":\"checkout\",\"type\":\"boolean\"}")
        .post(this.project.flagsPath())
        .then()
        .statusCode(400)
        .body("errors[0].message", equalTo("is required"));
  }

  @Test
  void anUnknownPropertyIsRejected() {
    as(this.project.devAdmin())
        .body("{\"key\":\"checkout\",\"surprise\":true}")
        .post(this.project.flagsPath())
        .then()
        .statusCode(400)
        .contentType(PROBLEM_JSON)
        .body("errors[0].field", equalTo("surprise"));
  }

  @Test
  void malformedJsonIsRejected() {
    as(this.project.devAdmin())
        .body("{\"key\":")
        .post(this.project.flagsPath())
        .then()
        .statusCode(400)
        .contentType(PROBLEM_JSON);
  }

  @Test
  void aVariantValueOfTheWrongTypeIsRejected() {
    as(this.project.devAdmin())
        .body(booleanFlag("checkout").replace("\"value\":true", "\"value\":\"yes\""))
        .post(this.project.flagsPath())
        .then()
        .statusCode(400)
        .body("errors[0].message", containsString("flag type"));
  }

  @Test
  void flagsAreListedSortedAndFiltered() {
    this.etagOfCreatedFlag("zeta");
    this.etagOfCreatedFlag("alpha");

    as(this.project.devAdmin())
        .get(this.project.flagsPath())
        .then()
        .statusCode(200)
        .body("key", equalTo(java.util.List.of("alpha", "zeta")));
    as(this.project.devAdmin())
        .queryParam("q", "ZET")
        .get(this.project.flagsPath())
        .then()
        .body("key", equalTo(java.util.List.of("zeta")));
    as(this.project.devAdmin())
        .queryParam("type", "string")
        .get(this.project.flagsPath())
        .then()
        .body("", empty());
    as(this.project.devAdmin())
        .queryParam("type", "colour")
        .get(this.project.flagsPath())
        .then()
        .statusCode(400);
  }

  @Test
  void togglingChangesOneEnvironmentAndBumpsTheRevision() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .header("If-Match", "\"1\"")
        .body("{\"enabled\":true}")
        .put(this.project.flagsPath() + "/checkout/environments/dev/enabled")
        .then()
        .statusCode(200)
        .header("ETag", equalTo("\"2\""))
        .body("environments.dev.enabled", equalTo(true))
        .body("environments.prod.enabled", equalTo(false));
  }

  @Test
  void aStaleIfMatchConflicts() {
    this.etagOfCreatedFlag("checkout");
    as(this.project.devAdmin())
        .body("{\"enabled\":true}")
        .put(this.project.flagsPath() + "/checkout/environments/dev/enabled")
        .then()
        .statusCode(200);

    as(this.project.devAdmin())
        .header("If-Match", "\"1\"")
        .body("{\"enabled\":false}")
        .put(this.project.flagsPath() + "/checkout/environments/dev/enabled")
        .then()
        .statusCode(409)
        .contentType(PROBLEM_JSON)
        .body("detail", containsString("revision"));
  }

  @Test
  void aWildcardIfMatchAndNoIfMatchBothApplyTheEdit() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .header("If-Match", "*")
        .body("{\"enabled\":true}")
        .put(this.project.flagsPath() + "/checkout/environments/dev/enabled")
        .then()
        .statusCode(200)
        .header("ETag", equalTo("\"2\""));
  }

  @Test
  void aMalformedIfMatchIsRejected() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .header("If-Match", "three")
        .body("{\"enabled\":true}")
        .put(this.project.flagsPath() + "/checkout/environments/dev/enabled")
        .then()
        .statusCode(400)
        .body("errors[0].field", equalTo("If-Match"));
  }

  @Test
  void anUnchangedEditKeepsTheRevision() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .body("{\"enabled\":false}")
        .put(this.project.flagsPath() + "/checkout/environments/dev/enabled")
        .then()
        .statusCode(200)
        .header("ETag", equalTo("\"1\""));
  }

  @Test
  void togglingWithoutAValueIsRejected() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .body("{}")
        .put(this.project.flagsPath() + "/checkout/environments/dev/enabled")
        .then()
        .statusCode(400);
  }

  @Test
  void targetingRulesAndRolloutsAreStoredAndReturned() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .body(settings(true, PLAN_RULE, ROLLOUT_60_40))
        .put(this.project.flagsPath() + "/checkout/environments/dev")
        .then()
        .statusCode(200)
        .body("environments.dev.rules[0].id", equalTo("beta"))
        .body("environments.dev.rules[0].conditions[0].operator", equalTo("in"))
        .body(
            "environments.dev.rules[0].conditions[0].values",
            equalTo(java.util.List.of("pro", "team")))
        .body("environments.dev.rules[0].serve.variant", equalTo("on"))
        .body("environments.dev.fallthrough.rollout[0].weight", equalTo(60000))
        .body("environments.prod.rules", empty());
  }

  @Test
  void aRolloutThatDoesNotAddUpIsRejected() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .body(
            settings(
                true,
                "[]",
                "{\"rollout\":[{\"variant\":\"on\",\"weight\":50000},{\"variant\":\"off\",\"weight\":10}]}"))
        .put(this.project.flagsPath() + "/checkout/environments/dev")
        .then()
        .statusCode(400)
        .body("errors[0].message", containsString("100000"));
  }

  @Test
  void aRuleWithAnUnknownOperatorIsRejected() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .body(settings(true, PLAN_RULE.replace("\"in\"", "\"nearly\""), ON))
        .put(this.project.flagsPath() + "/checkout/environments/dev")
        .then()
        .statusCode(400)
        .body("errors[0].message", containsString("nearly"));
  }

  @Test
  void aRuleThatServesAnUnknownVariantIsRejected() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .body(settings(true, "[]", "{\"variant\":\"maybe\"}"))
        .put(this.project.flagsPath() + "/checkout/environments/dev")
        .then()
        .statusCode(400);
  }

  @Test
  void theKillSwitchCanBeEngagedAndReleased() {
    this.etagOfCreatedFlag("checkout");
    String path = this.project.flagsPath() + "/checkout/environments/dev/kill-switch";

    as(this.project.devAdmin())
        .post(path)
        .then()
        .statusCode(200)
        .body("environments.dev.killSwitch", equalTo(true));
    as(this.project.devAdmin())
        .delete(path)
        .then()
        .statusCode(200)
        .body("environments.dev.killSwitch", equalTo(false));
  }

  @Test
  void theDefinitionOfAFlagCanBeUpdated() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .header("If-Match", "\"1\"")
        .body(
            """
            {"description":"new copy","variants":[{"key":"on","value":true},{"key":"off","value":false}]}
            """)
        .put(this.project.flagsPath() + "/checkout")
        .then()
        .statusCode(200)
        .header("ETag", equalTo("\"2\""))
        .body("description", equalTo("new copy"));
  }

  @Test
  void anArchivedFlagDisappearsFromTheListAndTheSnapshot() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devAdmin())
        .post(this.project.flagsPath() + "/checkout/archive")
        .then()
        .statusCode(200)
        .body("archived", equalTo(true));

    as(this.project.devAdmin()).get(this.project.flagsPath()).then().body("", empty());
    as(this.project.devAdmin())
        .queryParam("includeArchived", true)
        .get(this.project.flagsPath())
        .then()
        .body("key", equalTo(java.util.List.of("checkout")));
    as(this.project.devSdk()).get("/sdk/v1/snapshot").then().body("flags", empty());
  }

  @Test
  void aSegmentIsCreatedThenReplacedThenDeleted() {
    String path = this.project.segmentsPath("dev") + "/beta-users";
    String body =
        """
        {"name":"Beta users","included":["u2","u1"],"excluded":[],
         "rules":[[{"attribute":"plan","operator":"equals","values":["pro"]}]]}
        """;

    as(this.project.devAdmin())
        .body(body)
        .put(path)
        .then()
        .statusCode(201)
        .header("ETag", equalTo("\"1\""))
        .body("included", equalTo(java.util.List.of("u1", "u2")));
    as(this.project.devAdmin())
        .header("If-Match", "\"1\"")
        .body(body.replace("Beta users", "Beta cohort"))
        .put(path)
        .then()
        .statusCode(200)
        .header("ETag", equalTo("\"2\""))
        .body("name", equalTo("Beta cohort"));
    as(this.project.devAdmin())
        .get(this.project.segmentsPath("dev"))
        .then()
        .body("key", equalTo(java.util.List.of("beta-users")));
    as(this.project.devAdmin()).get(path).then().statusCode(200).body("revision", equalTo(2));
    as(this.project.devAdmin()).delete(path).then().statusCode(204);
    as(this.project.devAdmin()).get(path).then().statusCode(404);
  }

  @Test
  void aStaleSegmentEditConflicts() {
    String path = this.project.segmentsPath("dev") + "/beta-users";
    String body = "{\"name\":\"Beta\",\"included\":[],\"excluded\":[],\"rules\":[]}";
    as(this.project.devAdmin()).body(body).put(path).then().statusCode(201);
    as(this.project.devAdmin())
        .body(body.replace("Beta", "Beta 2"))
        .put(path)
        .then()
        .statusCode(200);

    as(this.project.devAdmin())
        .header("If-Match", "\"1\"")
        .body(body.replace("Beta", "Beta 3"))
        .put(path)
        .then()
        .statusCode(409);
    as(this.project.devAdmin()).header("If-Match", "\"1\"").delete(path).then().statusCode(409);
  }

  @Test
  void aSegmentInUseCannotBeDeletedAndAnUnknownSegmentCannotBeUsed() {
    this.etagOfCreatedFlag("checkout");
    String segmentRule =
        """
        [{"id":"seg","order":0,"conditions":[{"segment":"beta-users"}],"serve":{"variant":"on"}}]
        """;
    as(this.project.devAdmin())
        .body(settings(true, segmentRule, ON))
        .put(this.project.flagsPath() + "/checkout/environments/dev")
        .then()
        .statusCode(400);

    String path = this.project.segmentsPath("dev") + "/beta-users";
    as(this.project.devAdmin())
        .body("{\"name\":\"Beta\",\"included\":[\"u1\"],\"excluded\":[],\"rules\":[]}")
        .put(path)
        .then()
        .statusCode(201);
    as(this.project.devAdmin())
        .body(settings(true, segmentRule, ON))
        .put(this.project.flagsPath() + "/checkout/environments/dev")
        .then()
        .statusCode(200);

    as(this.project.devAdmin())
        .delete(path)
        .then()
        .statusCode(409)
        .body("detail", containsString("checkout"));
  }

  @Test
  void aSegmentRuleCannotRefertoAnotherSegment() {
    as(this.project.devAdmin())
        .body(
            "{\"name\":\"Nested\",\"included\":[],\"excluded\":[],\"rules\":[[{\"segment\":\"other\"}]]}")
        .put(this.project.segmentsPath("dev") + "/nested")
        .then()
        .statusCode(400);
  }

  @Test
  void anAdminKeyOfAnotherEnvironmentCannotWriteSegments() {
    as(this.project.admin().get("prod"))
        .body("{\"name\":\"Beta\",\"included\":[],\"excluded\":[],\"rules\":[]}")
        .put(this.project.segmentsPath("dev") + "/beta-users")
        .then()
        .statusCode(403);
  }

  @Test
  void everyChangeLeavesAnAuditEntryWithItsAuthor() {
    this.etagOfCreatedFlag("checkout");
    as(this.project.devAdmin())
        .body("{\"enabled\":true}")
        .put(this.project.flagsPath() + "/checkout/environments/dev/enabled")
        .then()
        .statusCode(200);

    as(this.project.devAdmin())
        .get("/api/v1/projects/" + this.project.key() + "/audit")
        .then()
        .statusCode(200)
        .body("[0].entityKey", equalTo("checkout"))
        .body("[0].author", equalTo("dev-admin"))
        .body("[0].action", equalTo("FlagToggled"))
        .body("[0].environment", equalTo("dev"))
        .body("[0].before.enabled", equalTo(false))
        .body("[0].after.enabled", equalTo(true))
        .body("entityKey", hasItem("checkout"));
  }

  @Test
  void theAuditLogCanBeFilteredAndPaged() {
    this.etagOfCreatedFlag("checkout");
    this.etagOfCreatedFlag("search");
    String audit = "/api/v1/projects/" + this.project.key() + "/audit";

    as(this.project.devAdmin())
        .queryParam("entity", "search")
        .get(audit)
        .then()
        .body("entityKey", not(hasItem("checkout")))
        .body("entityKey", hasItem("search"));
    as(this.project.devAdmin()).queryParam("limit", 1).get(audit).then().body("$", hasSize(1));
    java.util.List<String> everything = as(this.project.devAdmin()).get(audit).path("id");
    as(this.project.devAdmin())
        .queryParam("limit", 1)
        .queryParam("offset", 1)
        .get(audit)
        .then()
        .body("[0].id", equalTo(everything.get(1)));
    as(this.project.devAdmin()).queryParam("limit", 0).get(audit).then().statusCode(400);
    as(this.project.devSdk()).get(audit).then().statusCode(403);
  }

  @Test
  void theSnapshotHoldsTheCompiledFlagsOfTheKeysEnvironment() {
    this.etagOfCreatedFlag("checkout");
    as(this.project.devAdmin())
        .body(settings(true, PLAN_RULE, ROLLOUT_60_40))
        .put(this.project.flagsPath() + "/checkout/environments/dev")
        .then()
        .statusCode(200);

    as(this.project.devSdk())
        .get("/sdk/v1/snapshot")
        .then()
        .statusCode(200)
        .body("v", equalTo(2))
        .body("committedAtMs", notNullValue())
        .body("flags[0].key", equalTo("checkout"))
        .body("flags[0].enabled", equalTo(true))
        .body("flags[0].rules[0].serve.variant", equalTo("on"))
        .body("segments", empty());
    as(this.project.sdk().get("prod"))
        .get("/sdk/v1/snapshot")
        .then()
        .body("v", equalTo(1))
        .body("flags[0].enabled", equalTo(false));
  }

  @Test
  void aSnapshotOfAnUntouchedEnvironmentIsVersionZero() {
    as(this.project.devSdk())
        .get("/sdk/v1/snapshot")
        .then()
        .statusCode(200)
        .header("ETag", equalTo("\"0\""))
        .body("v", equalTo(0))
        .body("committedAtMs", nullValue())
        .body("flags", empty());
  }

  @Test
  void aMatchingIfNoneMatchAnswersNotModified() {
    this.etagOfCreatedFlag("checkout");

    as(this.project.devSdk())
        .header("If-None-Match", "\"1\"")
        .get("/sdk/v1/snapshot")
        .then()
        .statusCode(304)
        .header("ETag", equalTo("\"1\""));
    as(this.project.devSdk())
        .header("If-None-Match", "\"0\", W/\"7\"")
        .get("/sdk/v1/snapshot")
        .then()
        .statusCode(200);
  }

  @Test
  void theSnapshotNeedsAKey() {
    anonymous().get("/sdk/v1/snapshot").then().statusCode(401).contentType(PROBLEM_JSON);
  }

  @Test
  void jsonVariantsKeepTheirStructureInTheSnapshot() {
    as(this.project.devAdmin())
        .body(
            """
            {"key":"layout","description":"","type":"json",
             "variants":[{"key":"wide","value":{"cols":3,"tags":["a","b"],"ratio":1.5}},
                         {"key":"narrow","value":{"cols":1}}],
             "offVariant":"narrow","fallthroughVariant":"wide"}
            """)
        .post(this.project.flagsPath())
        .then()
        .statusCode(201);

    as(this.project.devSdk())
        .get("/sdk/v1/snapshot")
        .then()
        .body("flags[0].variants[0].value.cols", equalTo(3))
        .body("flags[0].variants[0].value.tags", equalTo(java.util.List.of("a", "b")))
        .body("flags[0].variants[0].value.ratio", equalTo(1.5f));
  }

  @Test
  void problemDetailsAreJson() {
    as(this.project.devAdmin())
        .get(this.project.flagsPath() + "/missing")
        .then()
        .contentType(ContentType.fromContentType(PROBLEM_JSON))
        .body("status", equalTo(404))
        .body("title", equalTo("Not found"));
  }
}
