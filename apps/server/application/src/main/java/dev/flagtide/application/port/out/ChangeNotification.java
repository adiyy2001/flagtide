package dev.flagtide.application.port.out;

import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.EnvironmentVersion;

public record ChangeNotification(EnvironmentRef environment, EnvironmentVersion version) {}
