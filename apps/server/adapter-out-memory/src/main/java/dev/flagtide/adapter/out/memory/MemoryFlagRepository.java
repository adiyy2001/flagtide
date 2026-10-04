package dev.flagtide.adapter.out.memory;

import dev.flagtide.application.port.out.FlagRepository;
import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.ProjectKey;
import dev.flagtide.domain.value.Revision;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MemoryFlagRepository implements FlagRepository {

  private final MemoryDatabase database;
  private final Map<ProjectKey, Map<FlagKey, Flag>> flags = new HashMap<>();

  public MemoryFlagRepository(MemoryDatabase database) {
    this.database = database;
  }

  @Override
  public Optional<Flag> find(ProjectKey project, FlagKey key) {
    return this.database.read(
        () -> Optional.ofNullable(this.flags.getOrDefault(project, Map.of()).get(key)));
  }

  @Override
  public List<Flag> findAll(ProjectKey project) {
    return this.database.read(
        () -> List.copyOf(this.flags.getOrDefault(project, Map.of()).values()));
  }

  @Override
  public void save(ProjectKey project, Flag flag, Revision expected) {
    this.database.write(
        () -> {
          Map<FlagKey, Flag> ofProject =
              this.flags.computeIfAbsent(project, key -> new HashMap<>());
          Optional<Flag> previous = Optional.ofNullable(ofProject.get(flag.key()));
          Revisions.requireMatch(
              "flag", flag.key().value(), previous.map(Flag::revision), expected);
          ofProject.put(flag.key(), flag);
          this.database.onRollback(
              () ->
                  previous.ifPresentOrElse(
                      old -> ofProject.put(flag.key(), old), () -> ofProject.remove(flag.key())));
          return null;
        });
  }
}
