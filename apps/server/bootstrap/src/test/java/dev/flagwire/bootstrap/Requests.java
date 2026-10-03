package dev.flagwire.bootstrap;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

final class Requests {

  static final String BOOLEAN_FLAG =
      """
      {"key":"%s","description":"checkout redesign","type":"boolean",
       "variants":[{"key":"on","value":true},{"key":"off","value":false}],
       "offVariant":"off","fallthroughVariant":"on"}
      """;

  private Requests() {}

  static RequestSpecification as(String key) {
    return given().header("Authorization", "Bearer " + key).contentType(ContentType.JSON);
  }

  static RequestSpecification anonymous() {
    return given().contentType(ContentType.JSON);
  }

  static String booleanFlag(String key) {
    return BOOLEAN_FLAG.formatted(key);
  }

  static String settings(boolean enabled, String rulesJson, String fallthroughJson) {
    return """
        {"enabled":%s,"offVariant":"off","rules":%s,"fallthrough":%s}
        """
        .formatted(enabled, rulesJson, fallthroughJson);
  }
}
