package dev.flagtide.adapter.in.rest;

import dev.flagtide.adapter.in.rest.dto.CreateFlagRequest;
import dev.flagtide.adapter.in.rest.dto.EnabledRequest;
import dev.flagtide.adapter.in.rest.dto.EnvironmentSettingsRequest;
import dev.flagtide.adapter.in.rest.dto.FlagResponse;
import dev.flagtide.adapter.in.rest.dto.Problem;
import dev.flagtide.adapter.in.rest.dto.UpdateFlagRequest;
import dev.flagtide.application.security.Principal;
import dev.flagtide.application.usecase.ArchiveFlag;
import dev.flagtide.application.usecase.CommandResult;
import dev.flagtide.application.usecase.ConfigureFlag;
import dev.flagtide.application.usecase.CreateFlag;
import dev.flagtide.application.usecase.EngageKillSwitch;
import dev.flagtide.application.usecase.GetFlag;
import dev.flagtide.application.usecase.ListFlags;
import dev.flagtide.application.usecase.ReleaseKillSwitch;
import dev.flagtide.application.usecase.ToggleFlag;
import dev.flagtide.application.usecase.UpdateFlagDefinition;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.FlagType;
import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.FlagKey;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/api/v1/projects/{project}/flags")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Flags")
public class FlagResource {

  private static final String IF_MATCH_DESCRIPTION =
      "Entity tag of the revision the edit is based on. A stale tag gives 409. Optional.";

  private final Caller caller;
  private final ListFlags listFlags;
  private final GetFlag getFlag;
  private final CreateFlag createFlag;
  private final UpdateFlagDefinition updateFlagDefinition;
  private final ArchiveFlag archiveFlag;
  private final ConfigureFlag configureFlag;
  private final ToggleFlag toggleFlag;
  private final EngageKillSwitch engageKillSwitch;
  private final ReleaseKillSwitch releaseKillSwitch;

  @Inject
  public FlagResource(
      Caller caller,
      ListFlags listFlags,
      GetFlag getFlag,
      CreateFlag createFlag,
      UpdateFlagDefinition updateFlagDefinition,
      ArchiveFlag archiveFlag,
      ConfigureFlag configureFlag,
      ToggleFlag toggleFlag,
      EngageKillSwitch engageKillSwitch,
      ReleaseKillSwitch releaseKillSwitch) {
    this.caller = caller;
    this.listFlags = listFlags;
    this.getFlag = getFlag;
    this.createFlag = createFlag;
    this.updateFlagDefinition = updateFlagDefinition;
    this.archiveFlag = archiveFlag;
    this.configureFlag = configureFlag;
    this.toggleFlag = toggleFlag;
    this.engageKillSwitch = engageKillSwitch;
    this.releaseKillSwitch = releaseKillSwitch;
  }

  @GET
  @Operation(
      summary = "List flags",
      description = "Sorted by key. Archived flags are hidden by default.")
  public List<FlagResponse> list(
      @PathParam("project") String project,
      @QueryParam("q") String text,
      @QueryParam("type") String type,
      @QueryParam("includeArchived") @DefaultValue("false") boolean includeArchived) {
    Principal principal = this.caller.inProject(project);
    ListFlags.Query query =
        new ListFlags.Query(
            Optional.ofNullable(text).filter(value -> !value.isBlank()),
            Optional.ofNullable(type).filter(value -> !value.isBlank()).map(FlagResource::flagType),
            includeArchived);
    return this.listFlags.execute(principal, query).stream().map(FlagResponse::from).toList();
  }

  @POST
  @Operation(summary = "Create a flag in every environment of the project")
  @APIResponse(
      responseCode = "201",
      description = "Created",
      headers = {
        @Header(name = "ETag", description = "Entity tag of the new revision"),
        @Header(name = "Location")
      },
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = FlagResponse.class)))
  @APIResponse(
      responseCode = "409",
      description = "The key exists",
      content =
          @Content(
              mediaType = Problem.MEDIA_TYPE,
              schema = @Schema(implementation = Problem.class)))
  public RestResponse<FlagResponse> create(
      @PathParam("project") String project, CreateFlagRequest request) {
    Principal principal = this.caller.inProject(project);
    Flag flag =
        this.createFlag.execute(principal, Required.field("body", request).toCommand()).value();
    return RestResponse.ResponseBuilder.<FlagResponse>created(
            URI.create("/api/v1/projects/" + project + "/flags/" + flag.key().value()))
        .tag(Preconditions.tag(flag.revision()))
        .entity(FlagResponse.from(flag))
        .build();
  }

  @GET
  @Path("{key}")
  @Operation(summary = "Read a flag", description = "The ETag header carries the revision.")
  @APIResponse(
      responseCode = "200",
      description = "The flag",
      headers = @Header(name = "ETag"),
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = FlagResponse.class)))
  public RestResponse<FlagResponse> get(
      @PathParam("project") String project, @PathParam("key") String key) {
    Principal principal = this.caller.inProject(project);
    return this.respond(this.getFlag.execute(principal, new FlagKey(key)));
  }

  @PUT
  @Path("{key}")
  @Operation(summary = "Change description and variants")
  public RestResponse<FlagResponse> update(
      @PathParam("project") String project,
      @PathParam("key") String key,
      @HeaderParam("If-Match") @Parameter(description = IF_MATCH_DESCRIPTION) String ifMatch,
      UpdateFlagRequest request) {
    Principal principal = this.caller.inProject(project);
    CommandResult<Flag> result =
        this.updateFlagDefinition.execute(
            principal,
            Required.field("body", request)
                .toCommand(new FlagKey(key), Preconditions.expectedRevision(ifMatch)));
    return this.respond(result.value());
  }

  @POST
  @Path("{key}/archive")
  @Operation(summary = "Archive a flag")
  public RestResponse<FlagResponse> archive(
      @PathParam("project") String project,
      @PathParam("key") String key,
      @HeaderParam("If-Match") @Parameter(description = IF_MATCH_DESCRIPTION) String ifMatch) {
    Principal principal = this.caller.inProject(project);
    CommandResult<Flag> result =
        this.archiveFlag.execute(
            principal,
            new ArchiveFlag.Command(new FlagKey(key), Preconditions.expectedRevision(ifMatch)));
    return this.respond(result.value());
  }

  @PUT
  @Path("{key}/environments/{environment}")
  @Operation(
      summary = "Replace the targeting of a flag in one environment",
      description = "Needs the admin key of that environment.")
  public RestResponse<FlagResponse> configure(
      @PathParam("project") String project,
      @PathParam("key") String key,
      @PathParam("environment") String environment,
      @HeaderParam("If-Match") @Parameter(description = IF_MATCH_DESCRIPTION) String ifMatch,
      EnvironmentSettingsRequest request) {
    Principal principal = this.caller.inProject(project);
    CommandResult<Flag> result =
        this.configureFlag.execute(
            principal,
            new ConfigureFlag.Command(
                new FlagKey(key),
                Preconditions.expectedRevision(ifMatch),
                new EnvironmentKey(environment),
                Required.field("body", request).toDomain()));
    return this.respond(result.value());
  }

  @PUT
  @Path("{key}/environments/{environment}/enabled")
  @Operation(summary = "Switch a flag on or off in one environment")
  public RestResponse<FlagResponse> toggle(
      @PathParam("project") String project,
      @PathParam("key") String key,
      @PathParam("environment") String environment,
      @HeaderParam("If-Match") @Parameter(description = IF_MATCH_DESCRIPTION) String ifMatch,
      EnabledRequest request) {
    Principal principal = this.caller.inProject(project);
    CommandResult<Flag> result =
        this.toggleFlag.execute(
            principal,
            new ToggleFlag.Command(
                new FlagKey(key),
                Preconditions.expectedRevision(ifMatch),
                new EnvironmentKey(environment),
                Required.field("enabled", Required.field("body", request).enabled())));
    return this.respond(result.value());
  }

  @POST
  @Path("{key}/environments/{environment}/kill-switch")
  @Operation(
      summary = "Engage the kill switch",
      description = "Serves the off variant in that environment regardless of targeting.")
  public RestResponse<FlagResponse> engageKillSwitch(
      @PathParam("project") String project,
      @PathParam("key") String key,
      @PathParam("environment") String environment) {
    Principal principal = this.caller.inProject(project);
    return this.respond(
        this.engageKillSwitch
            .execute(
                principal,
                new EngageKillSwitch.Command(new FlagKey(key), new EnvironmentKey(environment)))
            .value());
  }

  @DELETE
  @Path("{key}/environments/{environment}/kill-switch")
  @Operation(summary = "Release the kill switch")
  public RestResponse<FlagResponse> releaseKillSwitch(
      @PathParam("project") String project,
      @PathParam("key") String key,
      @PathParam("environment") String environment) {
    Principal principal = this.caller.inProject(project);
    return this.respond(
        this.releaseKillSwitch
            .execute(
                principal,
                new ReleaseKillSwitch.Command(new FlagKey(key), new EnvironmentKey(environment)))
            .value());
  }

  private RestResponse<FlagResponse> respond(Flag flag) {
    return RestResponse.ResponseBuilder.ok(FlagResponse.from(flag))
        .tag(Preconditions.tag(flag.revision()))
        .build();
  }

  private static FlagType flagType(String name) {
    try {
      return FlagType.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      throw FlagtideException.invalid("type", "must be boolean, string, number or json");
    }
  }
}
