package dev.flagtide.domain.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlagtideErrorTest {

  @Test
  void describesEachKindOfError() {
    assertThat(new FlagtideError.NotFound("flag", "x").message())
        .isEqualTo("flag x does not exist");
    assertThat(new FlagtideError.Conflict("stale").message()).isEqualTo("stale");
    assertThat(new FlagtideError.Unauthorized("no key").message()).isEqualTo("no key");
    assertThat(new FlagtideError.Forbidden("no").message()).isEqualTo("no");
    assertThat(
            new FlagtideError.ValidationFailed(
                    List.of(new Violation("a", "is bad"), new Violation("b", "is worse")))
                .message())
        .isEqualTo("a: is bad; b: is worse");
    assertThat(new FlagtideError.ValidationFailed(List.of()).message())
        .isEqualTo("validation failed");
  }

  @Test
  void carriesTheErrorInTheException() {
    FlagtideException exception = FlagtideException.notFound("flag", "x");

    assertThat(exception.error()).isEqualTo(new FlagtideError.NotFound("flag", "x"));
    assertThat(exception).hasMessage("flag x does not exist");
    assertThat(FlagtideException.conflict("c").error()).isInstanceOf(FlagtideError.Conflict.class);
    assertThat(FlagtideException.unauthorized("u").error())
        .isInstanceOf(FlagtideError.Unauthorized.class);
    assertThat(FlagtideException.forbidden("f").error())
        .isInstanceOf(FlagtideError.Forbidden.class);
    assertThat(FlagtideException.invalid("field", "bad").error())
        .isEqualTo(new FlagtideError.ValidationFailed(List.of(new Violation("field", "bad"))));
    assertThat(FlagtideException.invalid(List.of(new Violation("a", "b"))).error())
        .isInstanceOf(FlagtideError.ValidationFailed.class);
  }
}
