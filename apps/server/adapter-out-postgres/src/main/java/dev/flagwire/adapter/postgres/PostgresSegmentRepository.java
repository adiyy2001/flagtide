package dev.flagwire.adapter.postgres;

import dev.flagwire.application.port.out.SegmentRepository;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.segment.Segment;
import dev.flagwire.domain.segment.SegmentDocument;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.Revision;
import dev.flagwire.domain.value.SegmentKey;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Update;

public final class PostgresSegmentRepository implements SegmentRepository {

  private static final String INSERT =
      """
      INSERT INTO segments (project_key, environment_key, segment_key, revision, document, updated_at)
      VALUES (:project, :environment, :segment, :revision, %s, :updatedAt)
      ON CONFLICT (project_key, environment_key, segment_key) DO NOTHING
      """
          .formatted(Documents.jsonb("document"));

  private static final String UPDATE =
      """
      UPDATE segments
      SET revision = :revision, document = %s, updated_at = :updatedAt
      WHERE project_key = :project AND environment_key = :environment AND segment_key = :segment
        AND revision = :expected
      """
          .formatted(Documents.jsonb("document"));

  private static final String DELETE =
      """
      DELETE FROM segments
      WHERE project_key = :project AND environment_key = :environment AND segment_key = :segment
        AND revision = :expected
      """;

  private static final String SELECT_ONE =
      """
      SELECT document FROM segments
      WHERE project_key = :project AND environment_key = :environment AND segment_key = :segment
      """;

  private static final String SELECT_ALL =
      """
      SELECT document FROM segments
      WHERE project_key = :project AND environment_key = :environment
      ORDER BY segment_key
      """;

  private static final String SELECT_REVISION =
      """
      SELECT revision FROM segments
      WHERE project_key = :project AND environment_key = :environment AND segment_key = :segment
      """;

  private final PostgresDatabase database;

  public PostgresSegmentRepository(PostgresDatabase database) {
    this.database = database;
  }

  @Override
  public Optional<Segment> find(EnvironmentRef environment, SegmentKey key) {
    return this.database.query(
        handle ->
            handle
                .createQuery(SELECT_ONE)
                .bind("project", environment.project().value())
                .bind("environment", environment.environment().value())
                .bind("segment", key.value())
                .map(
                    (rows, context) ->
                        SegmentDocument.fromJson(Documents.document(rows, "document")))
                .findOne());
  }

  @Override
  public List<Segment> findAll(EnvironmentRef environment) {
    return this.database.query(
        handle ->
            handle
                .createQuery(SELECT_ALL)
                .bind("project", environment.project().value())
                .bind("environment", environment.environment().value())
                .map(
                    (rows, context) ->
                        SegmentDocument.fromJson(Documents.document(rows, "document")))
                .list());
  }

  @Override
  public void save(EnvironmentRef environment, Segment segment, Revision expected) {
    this.database.query(
        handle -> {
          int changed =
              expected.equals(Revision.NONE)
                  ? this.bindSegment(handle.createUpdate(INSERT), environment, segment).execute()
                  : this.bindSegment(handle.createUpdate(UPDATE), environment, segment)
                      .bind("expected", expected.value())
                      .execute();
          if (changed == 0) {
            throw RevisionConflicts.conflict(
                "segment",
                segment.key().value(),
                this.storedRevision(handle, environment, segment.key()),
                expected);
          }
          return null;
        });
  }

  @Override
  public void delete(EnvironmentRef environment, SegmentKey key, Revision expected) {
    this.database.query(
        handle -> {
          int changed =
              handle
                  .createUpdate(DELETE)
                  .bind("project", environment.project().value())
                  .bind("environment", environment.environment().value())
                  .bind("segment", key.value())
                  .bind("expected", expected.value())
                  .execute();
          if (changed == 0) {
            OptionalLong stored = this.storedRevision(handle, environment, key);
            if (stored.isEmpty()) {
              throw FlagwireException.notFound("segment", key.value());
            }
            throw RevisionConflicts.conflict("segment", key.value(), stored, expected);
          }
          return null;
        });
  }

  private Update bindSegment(Update update, EnvironmentRef environment, Segment segment) {
    return update
        .bind("project", environment.project().value())
        .bind("environment", environment.environment().value())
        .bind("segment", segment.key().value())
        .bind("revision", segment.revision().value())
        .bind("document", Documents.text(SegmentDocument.toJson(segment)))
        .bind("updatedAt", Documents.toDatabase(segment.updatedAt()));
  }

  private OptionalLong storedRevision(Handle handle, EnvironmentRef environment, SegmentKey key) {
    return handle
        .createQuery(SELECT_REVISION)
        .bind("project", environment.project().value())
        .bind("environment", environment.environment().value())
        .bind("segment", key.value())
        .mapTo(Long.class)
        .findOne()
        .map(OptionalLong::of)
        .orElseGet(OptionalLong::empty);
  }
}
