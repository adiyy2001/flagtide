package dev.flagtide.application.port.out;

import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.ProjectKey;
import dev.flagtide.domain.value.Revision;
import java.util.List;
import java.util.Optional;

public interface FlagRepository {

  Optional<Flag> find(ProjectKey project, FlagKey key);

  List<Flag> findAll(ProjectKey project);

  void save(ProjectKey project, Flag flag, Revision expected);
}
