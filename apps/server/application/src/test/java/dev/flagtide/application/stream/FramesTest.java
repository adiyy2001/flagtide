package dev.flagtide.application.stream;

import static org.assertj.core.api.Assertions.assertThat;

import dev.flagtide.application.change.ChangeLogEntry;
import dev.flagtide.application.sync.Snapshot;
import dev.flagtide.application.testing.Samples;
import dev.flagtide.domain.json.JsonFields;
import dev.flagtide.domain.json.JsonText;
import dev.flagtide.domain.value.EnvironmentVersion;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FramesTest {

  private static JsonFields parse(String frame) {
    return JsonFields.of("frame", JsonText.parse(frame));
  }

  @Test
  void writesAnErrorFrame() {
    JsonFields frame = parse(Frames.error(4400, "bad frame"));

    assertThat(frame.text("t")).isEqualTo("error");
    assertThat(frame.integer("code")).isEqualTo(4400);
    assertThat(frame.text("message")).isEqualTo("bad frame");
  }

  @Test
  void writesCommitTimesAsEpochMilliseconds() {
    ChangeLogEntry entry =
        new ChangeLogEntry(EnvironmentVersion.of(3), Samples.NOW, Samples.upsert("a"));

    JsonFields frame = parse(Frames.deltas(2, 3, List.of(entry)));

    JsonFields written = JsonFields.of("entry", frame.array("entries").getFirst());
    assertThat(written.integer("committedAtMs")).isEqualTo(Samples.NOW.toInstant().toEpochMilli());
    assertThat(written.integer("v")).isEqualTo(3);
  }

  @Test
  void aSnapshotWithoutACommitHasNoCommitTime() {
    Snapshot empty = new Snapshot(EnvironmentVersion.ZERO, Optional.empty(), List.of(), List.of());

    JsonFields frame = parse(Frames.snapshot(empty));

    assertThat(frame.find("committedAtMs")).isEmpty();
    assertThat(frame.integer("v")).isZero();
  }
}
