package dev.flagwire.adapter.postgres;

import dev.flagwire.application.port.out.FlagRepository;
import dev.flagwire.domain.flag.Flag;
import dev.flagwire.domain.flag.FlagDocument;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.ProjectKey;
import dev.flagwire.domain.value.Revision;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Update;

public final class PostgresFlagRepository implements FlagRepository {

  private static final String INSERT =
      """
      INSERT INTO flags (project_key, flag_key, revision, archived, document, updated_at)
      VALUES (:project, :flag, :revision, :archived, %s, :updatedAt)
      ON CONFLICT (project_key, flag_key) DO NOTHING
      """
          .formatted(Documents.jsonb("document"));

  private static final String UPDATE =
      """
      UPDATE flags
      SET revision = :revision, archived = :archived, document = %s, updated_at = :updatedAt
      WHERE project_key = :project AND flag_key = :flag AND revision = :expected
      """
          .formatted(Documents.jsonb("document"));

  private static final String SELECT_ONE =
      "SELECT document FROM flags WHERE project_key = :project AND flag_key = :flag";

  private static final String SELECT_ALL =
      "SELECT document FROM flags WHERE project_key = :project ORDER BY flag_key";

  private static final String SELECT_REVISION =
      "SELECT revision FROM flags WHERE project_key = :project AND flag_key = :flag";

  private final PostgresDatabase database;

  public PostgresFlagRepository(PostgresDatabase database) {
    this.database = database;
  }

  @Override
  public Optional<Flag> find(ProjectKey project, FlagKey key) {
    return this.database.query(
        handle ->
            handle
                .createQuery(SELECT_ONE)
                .bind("project", project.value())
                .bind("flag", key.value())
                .map((rows, context) -> FlagDocument.fromJson(Documents.document(rows, "document")))
                .findOne());
  }

  @Override
  public List<Flag> findAll(ProjectKey project) {
    return this.database.query(
        handle ->
            handle
                .createQuery(SELECT_ALL)
                .bind("project", project.value())
                .map((rows, context) -> FlagDocument.fromJson(Documents.document(rows, "document")))
                .list());
  }

  @Override
  public void save(ProjectKey project, Flag flag, Revision expected) {
    this.database.query(
        handle -> {
          int changed =
              expected.equals(Revision.NONE)
                  ? this.insert(handle, project, flag)
                  : this.update(handle, project, flag, expected);
          if (changed == 0) {
            throw RevisionConflicts.conflict(
                "flag", flag.key().value(), this.storedRevision(handle, project, flag), expected);
          }
          return null;
        });
  }

  private int insert(Handle handle, ProjectKey project, Flag flag) {
    return this.bindFlag(handle.createUpdate(INSERT), project, flag).execute();
  }

  private int update(Handle handle, ProjectKey project, Flag flag, Revision expected) {
    return this.bindFlag(handle.createUpdate(UPDATE), project, flag)
        .bind("expected", expected.value())
        .execute();
  }

  private Update bindFlag(Update update, ProjectKey project, Flag flag) {
    return update
        .bind("project", project.value())
        .bind("flag", flag.key().value())
        .bind("revision", flag.revision().value())
        .bind("archived", flag.archived())
        .bind("document", Documents.text(FlagDocument.toJson(flag)))
        .bind("updatedAt", Documents.toDatabase(flag.updatedAt()));
  }

  private OptionalLong storedRevision(Handle handle, ProjectKey project, Flag flag) {
    return handle
        .createQuery(SELECT_REVISION)
        .bind("project", project.value())
        .bind("flag", flag.key().value())
        .mapTo(Long.class)
        .findOne()
        .map(OptionalLong::of)
        .orElseGet(OptionalLong::empty);
  }
}
