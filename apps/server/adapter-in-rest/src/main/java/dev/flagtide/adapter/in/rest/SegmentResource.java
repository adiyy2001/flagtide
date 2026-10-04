package dev.flagtide.adapter.in.rest;

import dev.flagtide.adapter.in.rest.dto.SegmentRequest;
import dev.flagtide.adapter.in.rest.dto.SegmentResponse;
import dev.flagtide.application.security.Principal;
import dev.flagtide.application.usecase.DeleteSegment;
import dev.flagtide.application.usecase.ListSegments;
import dev.flagtide.application.usecase.SaveSegment;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.segment.Segment;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.Revision;
import dev.flagtide.domain.value.SegmentKey;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/api/v1/projects/{project}/environments/{environment}/segments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Segments")
public class SegmentResource {

  private static final String IF_MATCH_DESCRIPTION =
      "Entity tag of the revision the edit is based on. A stale tag gives 409. Optional.";

  private final Caller caller;
  private final ListSegments listSegments;
  private final SaveSegment saveSegment;
  private final DeleteSegment deleteSegment;

  @Inject
  public SegmentResource(
      Caller caller,
      ListSegments listSegments,
      SaveSegment saveSegment,
      DeleteSegment deleteSegment) {
    this.caller = caller;
    this.listSegments = listSegments;
    this.saveSegment = saveSegment;
    this.deleteSegment = deleteSegment;
  }

  @GET
  @Operation(summary = "List the segments of an environment")
  public List<SegmentResponse> list(
      @PathParam("project") String project, @PathParam("environment") String environment) {
    Principal principal = this.caller.inProject(project);
    return this.listSegments.execute(principal, new EnvironmentKey(environment)).stream()
        .map(SegmentResponse::from)
        .toList();
  }

  @GET
  @Path("{key}")
  @Operation(summary = "Read a segment")
  public RestResponse<SegmentResponse> get(
      @PathParam("project") String project,
      @PathParam("environment") String environment,
      @PathParam("key") String key) {
    Principal principal = this.caller.inProject(project);
    SegmentKey segmentKey = new SegmentKey(key);
    Segment segment =
        this.listSegments.execute(principal, new EnvironmentKey(environment)).stream()
            .filter(candidate -> candidate.key().equals(segmentKey))
            .findFirst()
            .orElseThrow(() -> FlagtideException.notFound("segment", key));
    return this.respond(segment, RestResponse.Status.OK);
  }

  @PUT
  @Path("{key}")
  @Operation(
      summary = "Create or replace a segment",
      description = "Answers 201 when the segment is new and 200 when it was replaced.")
  @APIResponse(
      responseCode = "201",
      description = "Created",
      headers = @Header(name = "ETag"),
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = SegmentResponse.class)))
  @APIResponse(
      responseCode = "200",
      description = "Replaced",
      headers = @Header(name = "ETag"),
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = SegmentResponse.class)))
  public RestResponse<SegmentResponse> save(
      @PathParam("project") String project,
      @PathParam("environment") String environment,
      @PathParam("key") String key,
      @HeaderParam("If-Match") @Parameter(description = IF_MATCH_DESCRIPTION) String ifMatch,
      SegmentRequest request) {
    Principal principal = this.caller.inProject(project);
    Segment saved =
        this.saveSegment
            .execute(
                principal,
                Required.field("body", request)
                    .toCommand(
                        new EnvironmentKey(environment),
                        new SegmentKey(key),
                        Preconditions.expectedRevision(ifMatch)))
            .value();
    RestResponse.Status status =
        saved.revision().equals(Revision.FIRST)
            ? RestResponse.Status.CREATED
            : RestResponse.Status.OK;
    return this.respond(saved, status);
  }

  @DELETE
  @Path("{key}")
  @Operation(
      summary = "Delete a segment",
      description = "Answers 409 while an active flag still refers to the segment.")
  public RestResponse<Void> delete(
      @PathParam("project") String project,
      @PathParam("environment") String environment,
      @PathParam("key") String key,
      @HeaderParam("If-Match") @Parameter(description = IF_MATCH_DESCRIPTION) String ifMatch) {
    Principal principal = this.caller.inProject(project);
    this.deleteSegment.execute(
        principal,
        new DeleteSegment.Command(
            new EnvironmentKey(environment),
            new SegmentKey(key),
            Preconditions.expectedRevision(ifMatch)));
    return RestResponse.noContent();
  }

  private RestResponse<SegmentResponse> respond(Segment segment, RestResponse.Status status) {
    return RestResponse.ResponseBuilder.create(status, SegmentResponse.from(segment))
        .tag(Preconditions.tag(segment.revision()))
        .build();
  }
}
