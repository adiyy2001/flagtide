package dev.flagtide.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.flagtide.domain.error.FlagtideError;
import dev.flagtide.domain.error.Violation;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Problem", description = "RFC 9457 problem details")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Problem(
    String type, String title, int status, String detail, List<FieldProblem> errors) {

  public static final String MEDIA_TYPE = "application/problem+json";

  public record FieldProblem(String field, String message) {

    static FieldProblem from(Violation violation) {
      return new FieldProblem(violation.field(), violation.message());
    }
  }

  public static Problem from(FlagtideError error) {
    return switch (error) {
      case FlagtideError.NotFound notFound ->
          of("not-found", "Not found", 404, notFound.message(), null);
      case FlagtideError.Conflict conflict ->
          of("conflict", "Conflict", 409, conflict.message(), null);
      case FlagtideError.ValidationFailed failed ->
          of(
              "validation",
              "Validation failed",
              400,
              failed.message(),
              failed.violations().stream().map(FieldProblem::from).toList());
      case FlagtideError.Unauthorized unauthorized ->
          of("unauthorized", "Unauthorized", 401, unauthorized.message(), null);
      case FlagtideError.Forbidden forbidden ->
          of("forbidden", "Forbidden", 403, forbidden.message(), null);
    };
  }

  public static Problem of(String code, String title, int status, String detail) {
    return of(code, title, status, detail, null);
  }

  private static Problem of(
      String code, String title, int status, String detail, List<FieldProblem> errors) {
    return new Problem("urn:flagtide:problem:" + code, title, status, detail, errors);
  }
}
