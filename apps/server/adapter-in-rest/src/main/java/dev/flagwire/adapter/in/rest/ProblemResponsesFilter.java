package dev.flagwire.adapter.in.rest;

import dev.flagwire.adapter.in.rest.dto.Problem;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;

public class ProblemResponsesFilter implements OASFilter {

  private static final String PROBLEM_SCHEMA = "#/components/schemas/Problem";

  @Override
  public void filterOpenAPI(OpenAPI openAPI) {
    if (openAPI.getPaths() == null) {
      return;
    }
    openAPI
        .getPaths()
        .getPathItems()
        .forEach(
            (path, item) ->
                item.getOperations()
                    .forEach((method, operation) -> this.describe(path, method, operation)));
  }

  private void describe(String path, PathItem.HttpMethod method, Operation operation) {
    this.add(operation, "401", "Missing or unknown key");
    this.add(operation, "403", "The key may not do this");
    this.add(operation, "400", "Malformed or invalid input");
    if (path.contains("{key}") || path.contains("{environment}")) {
      this.add(operation, "404", "Not found");
    }
    if (method != PathItem.HttpMethod.GET) {
      this.add(operation, "409", "Conflict with the current state or a stale If-Match");
    }
  }

  private void add(Operation operation, String status, String description) {
    if (operation.getResponses() == null) {
      operation.setResponses(OASFactory.createAPIResponses());
    }
    Map<String, APIResponse> existing = operation.getResponses().getAPIResponses();
    if (existing.containsKey(status)) {
      return;
    }
    operation
        .getResponses()
        .addAPIResponse(
            status,
            OASFactory.createAPIResponse()
                .description(description)
                .content(
                    OASFactory.createContent()
                        .addMediaType(
                            Problem.MEDIA_TYPE,
                            OASFactory.createMediaType()
                                .schema(OASFactory.createSchema().ref(PROBLEM_SCHEMA)))));
  }
}
