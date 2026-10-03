package dev.flagwire.adapter.in.rest;

import dev.flagwire.adapter.in.rest.dto.PropagationResponse;
import dev.flagwire.application.security.Principal;
import dev.flagwire.application.usecase.GetPropagation;
import dev.flagwire.domain.value.EnvironmentKey;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/projects/{project}/environments/{environment}/propagation")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Propagation")
public class PropagationResource {

  private final Caller caller;
  private final GetPropagation getPropagation;

  @Inject
  public PropagationResource(Caller caller, GetPropagation getPropagation) {
    this.caller = caller;
    this.getPropagation = getPropagation;
  }

  @GET
  @Operation(
      summary = "How fast changes reach connected SDK clients",
      description =
          "Percentiles of the time from commit to client acknowledgement, merged across all"
              + " instances over the last minute.")
  public PropagationResponse get(
      @PathParam("project") String project, @PathParam("environment") String environment) {
    Principal principal = this.caller.inProject(project);
    return PropagationResponse.from(
        this.getPropagation.execute(principal, new EnvironmentKey(environment)));
  }
}
