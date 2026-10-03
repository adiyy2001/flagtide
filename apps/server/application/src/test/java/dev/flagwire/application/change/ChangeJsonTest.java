package dev.flagwire.application.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.application.testing.Samples;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.FlagConfig;
import dev.flagwire.domain.evaluation.Segment;
import dev.flagwire.domain.flag.FlagCompiler;
import dev.flagwire.domain.json.JsonText;
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
        .isInstanceOf(FlagwireException.class)
        .hasMessageContaining("unknown change");
  }
}
