package dev.flagwire.adapter.in.rest;

import dev.flagwire.adapter.in.rest.dto.SnapshotResponse;
import dev.flagwire.application.security.Principal;
import dev.flagwire.application.sync.Snapshot;
import dev.flagwire.application.usecase.BuildSnapshot;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/sdk/v1/snapshot")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "SDK")
public class SdkResource {

  private final Caller caller;
  private final BuildSnapshot buildSnapshot;

  @Inject
  public SdkResource(Caller caller, BuildSnapshot buildSnapshot) {
    this.caller = caller;
    this.buildSnapshot = buildSnapshot;
  }

  @GET
  @Operation(
      summary = "Every active flag and segment of the key's environment",
      description =
          "The ETag is the environment version. A matching If-None-Match answers 304 with no"
              + " body.")
  @APIResponse(
      responseCode = "200",
      description = "The snapshot",
      headers = @Header(name = "ETag"),
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = SnapshotResponse.class)))
  @APIResponse(responseCode = "304", description = "The environment version is unchanged")
  public RestResponse<SnapshotResponse> snapshot(@HeaderParam("If-None-Match") String ifNoneMatch) {
    Principal principal = this.caller.principal();
    Snapshot snapshot = this.buildSnapshot.execute(principal);
    String tag = Preconditions.tag(snapshot.version().value());
    if (Preconditions.matches(ifNoneMatch, tag)) {
      return RestResponse.ResponseBuilder.<SnapshotResponse>create(RestResponse.Status.NOT_MODIFIED)
          .tag(tag)
          .build();
    }
    return RestResponse.ResponseBuilder.ok(SnapshotResponse.from(snapshot)).tag(tag).build();
  }
}
