package dev.flagwire.application.port.out;

import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.EnvironmentVersion;

public record ChangeNotification(EnvironmentRef environment, EnvironmentVersion version) {}
