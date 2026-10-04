package dev.flagtide.adapter.in.rest;

import dev.flagtide.adapter.in.rest.dto.AuditEntryResponse;
import dev.flagtide.application.security.Principal;
import dev.flagtide.application.usecase.ReadAuditLog;
import dev.flagtide.domain.value.EnvironmentKey;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/projects/{project}/audit")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Audit")
public class AuditResource {

  private final Caller caller;
  private final ReadAuditLog readAuditLog;

  @Inject
  public AuditResource(Caller caller, ReadAuditLog readAuditLog) {
    this.caller = caller;
    this.readAuditLog = readAuditLog;
  }

  @GET
  @Operation(summary = "Read the audit log, newest first")
  public List<AuditEntryResponse> list(
      @PathParam("project") String project,
      @QueryParam("environment") String environment,
      @QueryParam("entity") String entity,
      @QueryParam("limit") @DefaultValue("50") int limit,
      @QueryParam("offset") @DefaultValue("0") int offset) {
    Principal principal = this.caller.inProject(project);
    ReadAuditLog.Query query = ReadAuditLog.Query.newest(limit).skipping(offset);
    if (environment != null && !environment.isBlank()) {
      query = query.inEnvironment(new EnvironmentKey(environment));
    }
    if (entity != null && !entity.isBlank()) {
      query = query.aboutEntity(entity);
    }
    return this.readAuditLog.execute(principal, query).stream()
        .map(AuditEntryResponse::from)
        .toList();
  }
}
