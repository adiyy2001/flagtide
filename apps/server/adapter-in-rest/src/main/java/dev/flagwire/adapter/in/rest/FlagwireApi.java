package dev.flagwire.adapter.in.rest;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@OpenAPIDefinition(
    info =
        @Info(
            title = "flagwire admin API",
            version = "1.0.0",
            description =
                "Feature flags per project and environment. Admin keys (fwa_) write, SDK keys"
                    + " (fws_) read the snapshot. Edits may send If-Match with the ETag of the"
                    + " flag or segment. A stale tag answers 409."),
    security = @SecurityRequirement(name = "bearerKey"))
@SecurityScheme(
    securitySchemeName = "bearerKey",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    description = "An admin key or SDK key of one environment")
public class FlagwireApi extends Application {}
