package dev.flagtide.application.usecase;

import dev.flagtide.domain.access.ApiKey;

public record IssuedKey(ApiKey key, String secret) {}
