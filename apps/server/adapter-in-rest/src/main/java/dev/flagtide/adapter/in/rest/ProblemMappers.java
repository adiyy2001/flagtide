package dev.flagtide.adapter.in.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import dev.flagtide.adapter.in.rest.dto.Problem;
import dev.flagtide.domain.error.FlagtideError;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.error.Violation;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public final class ProblemMappers {

  private static final Logger LOG = Logger.getLogger(ProblemMappers.class);

  @ServerExceptionMapper
  public Response flagtide(FlagtideException exception) {
    Problem problem = Problem.from(exception.error());
    Response.ResponseBuilder response = this.builder(problem);
    if (problem.status() == 401) {
      response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }
    return response.build();
  }

  @ServerExceptionMapper
  public Response unknownProperty(UnrecognizedPropertyException exception) {
    return this.builder(
            Problem.from(
                new FlagtideError.ValidationFailed(
                    List.of(new Violation(exception.getPropertyName(), "is not a known field")))))
        .build();
  }

  @ServerExceptionMapper
  public Response unreadableBody(JsonProcessingException exception) {
    return this.problem(400, "malformed-body", "Malformed body", exception.getOriginalMessage());
  }

  @ServerExceptionMapper
  public Response webApplication(WebApplicationException exception) {
    Response original = exception.getResponse();
    int status = original.getStatus();
    if (status < 400) {
      return original;
    }
    return this.problem(
        status,
        "http-" + status,
        original.getStatusInfo().getReasonPhrase(),
        exception.getMessage());
  }

  @ServerExceptionMapper
  public Response unexpected(Throwable failure) {
    LOG.error("unhandled failure", failure);
    return this.problem(500, "internal", "Internal error", "the request could not be completed");
  }

  private Response problem(int status, String code, String title, String detail) {
    return this.builder(Problem.of(code, title, status, detail)).build();
  }

  private Response.ResponseBuilder builder(Problem problem) {
    return Response.status(problem.status()).type(Problem.MEDIA_TYPE).entity(problem);
  }
}
