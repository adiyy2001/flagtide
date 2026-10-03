package dev.flagwire.application.usecase;

import dev.flagwire.domain.access.ApiKey;

public record IssuedKey(ApiKey key, String secret) {}
