package dev.flagwire.adapter.in.rest.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Enabled")
public record EnabledRequest(Boolean enabled) {}
