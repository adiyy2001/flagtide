package dev.flagtide.adapter.in.rest;

import dev.flagtide.adapter.in.rest.dto.CreateEnvironmentRequest;
import dev.flagtide.adapter.in.rest.dto.KeyResponse;
import dev.flagtide.adapter.in.rest.dto.ProjectResponse;
import dev.flagtide.adapter.in.rest.dto.ProvisionedEnvironmentResponse;
import dev.flagtide.application.security.Principal;
import dev.flagtide.application.usecase.CreateEnvironment;
import dev.flagtide.application.usecase.GetProject;
import dev.flagtide.application.usecase.ListApiKeys;
import dev.flagtide.domain.value.EnvironmentKey;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.net.URI;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/api/v1/projects/{project}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Projects")
public class ProjectResource {

  private final Caller caller;
  private final GetProject getProject;
  private final CreateEnvironment createEnvironment;
  private final ListApiKeys listApiKeys;

  @Inject
  public ProjectResource(
      Caller caller,
      GetProject getProject,
      CreateEnvironment createEnvironment,
      ListApiKeys listApiKeys) {
    this.caller = caller;
    this.getProject = getProject;
    this.createEnvironment = createEnvironment;
    this.listApiKeys = listApiKeys;
  }

  @GET
  @Operation(summary = "Read the project and its environments")
  public ProjectResponse get(@PathParam("project") String project) {
    Principal principal = this.caller.inProject(project);
    return ProjectResponse.from(this.getProject.execute(principal));
  }

  @POST
  @Path("environments")
  @Operation(
      summary = "Add an environment",
      description =
          "Creates the environment with an admin key and an SDK key. The secrets are in this"
              + " response only.")
  @APIResponse(
      responseCode = "201",
      description = "Created",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = ProvisionedEnvironmentResponse.class)))
  public RestResponse<ProvisionedEnvironmentResponse> createEnvironment(
      @PathParam("project") String project, CreateEnvironmentRequest request) {
    Principal principal = this.caller.inProject(project);
    ProvisionedEnvironmentResponse created =
        ProvisionedEnvironmentResponse.from(
            this.createEnvironment
                .execute(principal, Required.field("body", request).toCommand())
                .value());
    return RestResponse.ResponseBuilder.<ProvisionedEnvironmentResponse>created(
            URI.create("/api/v1/projects/" + project))
        .entity(created)
        .build();
  }

  @GET
  @Path("environments/{environment}/keys")
  @Operation(summary = "List the keys of an environment")
  public List<KeyResponse> keys(
      @PathParam("project") String project, @PathParam("environment") String environment) {
    Principal principal = this.caller.inProject(project);
    return this.listApiKeys.execute(principal, new EnvironmentKey(environment)).stream()
        .map(KeyResponse::from)
        .toList();
  }
}
