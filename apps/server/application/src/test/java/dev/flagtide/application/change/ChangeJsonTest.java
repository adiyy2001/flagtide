package dev.flagtide.application.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.application.testing.Samples;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.FlagConfig;
import dev.flagtide.domain.evaluation.Segment;
import dev.flagtide.domain.flag.FlagCompiler;
import dev.flagtide.domain.json.JsonText;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeJsonTest {

  private static final FlagConfig CONFIG =
      FlagCompiler.compile(Samples.flag("checkout"), Samples.DEV);
  private static final Segment SEGMENT = FlagCompiler.compileSegment(Samples.segment("beta"));

  @Test
  void roundTripsEveryKindOfChange() {
    List<Change> changes =
        List.of(
            new Change.FlagUpserted(CONFIG),
            new Change.FlagRemoved("old"),
            new Change.SegmentUpserted(SEGMENT),
            new Change.SegmentRemoved("gone"));

    String text = JsonText.write(ChangeJson.toJson(changes));

    assertThat(ChangeJson.listFromJson(JsonText.parse(text))).isEqualTo(changes);
  }

  @Test
  void usesTheOperationKindAndKeyFieldsOfTheProtocol() {
    String text = JsonText.write(ChangeJson.toJson(new Change.FlagRemoved("old")));

    assertThat(text).isEqualTo("{\"key\":\"old\",\"kind\":\"flag\",\"op\":\"remove\"}");
  }

  @Test
  void rejectsAnUnknownOperation() {
    assertThatThrownBy(
            () ->
                ChangeJson.fromJson(
                    JsonText.parse("{\"op\":\"rename\",\"kind\":\"flag\",\"key\":\"x\"}")))
        .isInstanceOf(FlagtideException.class)
        .hasMessageContaining("unknown change");
  }
}
