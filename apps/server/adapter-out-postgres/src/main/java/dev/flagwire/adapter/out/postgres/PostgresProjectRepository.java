package dev.flagwire.adapter.out.postgres;

import dev.flagwire.application.port.out.ProjectRepository;
import dev.flagwire.domain.project.Project;
import dev.flagwire.domain.project.ProjectDocument;
import dev.flagwire.domain.value.ProjectKey;
import dev.flagwire.domain.value.Revision;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Update;

public final class PostgresProjectRepository implements ProjectRepository {

  private static final String INSERT =
      """
      INSERT INTO projects (project_key, revision, document, created_at)
      VALUES (:project, :revision, %s, :createdAt)
      ON CONFLICT (project_key) DO NOTHING
      """
          .formatted(Documents.jsonb("document"));

  private static final String UPDATE =
      """
      UPDATE projects SET revision = :revision, document = %s
      WHERE project_key = :project AND revision = :expected
      """
          .formatted(Documents.jsonb("document"));

  private static final String SELECT_ONE =
      "SELECT document FROM projects WHERE project_key = :project";

  private static final String SELECT_ALL = "SELECT document FROM projects ORDER BY project_key";

  private static final String SELECT_REVISION =
      "SELECT revision FROM projects WHERE project_key = :project";

  private final PostgresDatabase database;

  public PostgresProjectRepository(PostgresDatabase database) {
    this.database = database;
  }

  @Override
  public Optional<Project> find(ProjectKey key) {
    return this.database.query(
        handle ->
            handle
                .createQuery(SELECT_ONE)
                .bind("project", key.value())
                .map(
                    (rows, context) ->
                        ProjectDocument.fromJson(Documents.document(rows, "document")))
                .findOne());
  }

  @Override
  public List<Project> findAll() {
    return this.database.query(
        handle ->
            handle
                .createQuery(SELECT_ALL)
                .map(
                    (rows, context) ->
                        ProjectDocument.fromJson(Documents.document(rows, "document")))
                .list());
  }

  @Override
  public void save(Project project, Revision expected) {
    this.database.query(
        handle -> {
          int changed =
              expected.equals(Revision.NONE)
                  ? bind(handle.createUpdate(INSERT), project).execute()
                  : bind(handle.createUpdate(UPDATE), project)
                      .bind("expected", expected.value())
                      .execute();
          if (changed == 0) {
            throw RevisionConflicts.conflict(
                "project", project.key().value(), storedRevision(handle, project), expected);
          }
          return null;
        });
  }

  private static Update bind(Update update, Project project) {
    return update
        .bind("project", project.key().value())
        .bind("revision", project.revision().value())
        .bind("document", Documents.text(ProjectDocument.toJson(project)))
        .bind("createdAt", Documents.toDatabase(project.createdAt()));
  }

  private static OptionalLong storedRevision(Handle handle, Project project) {
    return handle
        .createQuery(SELECT_REVISION)
        .bind("project", project.key().value())
        .mapTo(Long.class)
        .findOne()
        .map(OptionalLong::of)
        .orElseGet(OptionalLong::empty);
  }
}
