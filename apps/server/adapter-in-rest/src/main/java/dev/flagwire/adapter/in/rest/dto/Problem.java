package dev.flagwire.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.flagwire.domain.error.FlagwireError;
import dev.flagwire.domain.error.Violation;
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

  public static Problem from(FlagwireError error) {
    return switch (error) {
      case FlagwireError.NotFound notFound ->
          of("not-found", "Not found", 404, notFound.message(), null);
      case FlagwireError.Conflict conflict ->
          of("conflict", "Conflict", 409, conflict.message(), null);
      case FlagwireError.ValidationFailed failed ->
          of(
              "validation",
              "Validation failed",
              400,
              failed.message(),
              failed.violations().stream().map(FieldProblem::from).toList());
      case FlagwireError.Unauthorized unauthorized ->
          of("unauthorized", "Unauthorized", 401, unauthorized.message(), null);
      case FlagwireError.Forbidden forbidden ->
          of("forbidden", "Forbidden", 403, forbidden.message(), null);
    };
  }

  public static Problem of(String code, String title, int status, String detail) {
    return of(code, title, status, detail, null);
  }

  private static Problem of(
      String code, String title, int status, String detail, List<FieldProblem> errors) {
    return new Problem("urn:flagwire:problem:" + code, title, status, detail, errors);
  }
}
