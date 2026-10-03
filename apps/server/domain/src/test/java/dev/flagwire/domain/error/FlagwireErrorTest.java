package dev.flagwire.domain.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlagwireErrorTest {

  @Test
  void describesEachKindOfError() {
    assertThat(new FlagwireError.NotFound("flag", "x").message())
        .isEqualTo("flag x does not exist");
    assertThat(new FlagwireError.Conflict("stale").message()).isEqualTo("stale");
    assertThat(new FlagwireError.Unauthorized("no key").message()).isEqualTo("no key");
    assertThat(new FlagwireError.Forbidden("no").message()).isEqualTo("no");
    assertThat(
            new FlagwireError.ValidationFailed(
                    List.of(new Violation("a", "is bad"), new Violation("b", "is worse")))
                .message())
        .isEqualTo("a: is bad; b: is worse");
    assertThat(new FlagwireError.ValidationFailed(List.of()).message())
        .isEqualTo("validation failed");
  }

  @Test
  void carriesTheErrorInTheException() {
    FlagwireException exception = FlagwireException.notFound("flag", "x");

    assertThat(exception.error()).isEqualTo(new FlagwireError.NotFound("flag", "x"));
    assertThat(exception).hasMessage("flag x does not exist");
    assertThat(FlagwireException.conflict("c").error()).isInstanceOf(FlagwireError.Conflict.class);
    assertThat(FlagwireException.unauthorized("u").error())
        .isInstanceOf(FlagwireError.Unauthorized.class);
    assertThat(FlagwireException.forbidden("f").error())
        .isInstanceOf(FlagwireError.Forbidden.class);
    assertThat(FlagwireException.invalid("field", "bad").error())
        .isEqualTo(new FlagwireError.ValidationFailed(List.of(new Violation("field", "bad"))));
    assertThat(FlagwireException.invalid(List.of(new Violation("a", "b"))).error())
        .isInstanceOf(FlagwireError.ValidationFailed.class);
  }
}
