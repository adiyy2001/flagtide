package dev.flagtide.domain.event;

import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.ProjectKey;
import dev.flagtide.domain.value.SegmentKey;
import java.util.Set;

public sealed interface DomainEvent {

  Stamp stamp();

  Set<EnvironmentKey> environments();

  record ProjectCreated(Stamp stamp, ProjectKey project) implements DomainEvent {
    @Override
    public Set<EnvironmentKey> environments() {
      return Set.of();
    }
  }

  record EnvironmentCreated(Stamp stamp, ProjectKey project, EnvironmentKey environment)
      implements DomainEvent {
    @Override
    public Set<EnvironmentKey> environments() {
      return Set.of();
    }
  }

  record FlagCreated(Stamp stamp, FlagKey flag, Set<EnvironmentKey> environments)
      implements DomainEvent {
    public FlagCreated {
      environments = Set.copyOf(environments);
    }
  }

  record FlagDefinitionChanged(Stamp stamp, FlagKey flag, Set<EnvironmentKey> environments)
      implements DomainEvent {
    public FlagDefinitionChanged {
      environments = Set.copyOf(environments);
    }
  }

  record FlagConfigChanged(Stamp stamp, FlagKey flag, EnvironmentKey environment)
      implements DomainEvent {
    @Override
    public Set<EnvironmentKey> environments() {
      return Set.of(this.environment);
    }
  }

  record FlagToggled(Stamp stamp, FlagKey flag, EnvironmentKey environment, boolean enabled)
      implements DomainEvent {
    @Override
    public Set<EnvironmentKey> environments() {
      return Set.of(this.environment);
    }
  }

  record KillSwitchEngaged(Stamp stamp, FlagKey flag, EnvironmentKey environment)
      implements DomainEvent {
    @Override
    public Set<EnvironmentKey> environments() {
      return Set.of(this.environment);
    }
  }

  record KillSwitchReleased(Stamp stamp, FlagKey flag, EnvironmentKey environment)
      implements DomainEvent {
    @Override
    public Set<EnvironmentKey> environments() {
      return Set.of(this.environment);
    }
  }

  record FlagArchived(Stamp stamp, FlagKey flag, Set<EnvironmentKey> environments)
      implements DomainEvent {
    public FlagArchived {
      environments = Set.copyOf(environments);
    }
  }

  record SegmentSaved(Stamp stamp, EnvironmentKey environment, SegmentKey segment, boolean created)
      implements DomainEvent {
    @Override
    public Set<EnvironmentKey> environments() {
      return Set.of(this.environment);
    }
  }

  record SegmentDeleted(Stamp stamp, EnvironmentKey environment, SegmentKey segment)
      implements DomainEvent {
    @Override
    public Set<EnvironmentKey> environments() {
      return Set.of(this.environment);
    }
  }
}
