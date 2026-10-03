package dev.flagwire.adapter.out.postgres;

import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.json.JsonText;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

final class Documents {

  private static final String CAST_TO_JSONB = "CAST(%s AS jsonb)";

  private Documents() {}

  static String jsonb(String parameter) {
    return CAST_TO_JSONB.formatted(":" + parameter);
  }

  static String text(JsonValue value) {
    return JsonText.write(value);
  }

  static Optional<String> optionalText(Optional<JsonValue> value) {
    return value.map(JsonText::write);
  }

  static JsonValue document(ResultSet rows, String column) throws SQLException {
    return JsonText.parse(rows.getString(column));
  }

  static Optional<JsonValue> optionalDocument(ResultSet rows, String column) throws SQLException {
    return Optional.ofNullable(rows.getString(column)).map(JsonText::parse);
  }

  static OffsetDateTime toDatabase(ZonedDateTime time) {
    return time.toOffsetDateTime();
  }

  static ZonedDateTime time(ResultSet rows, String column) throws SQLException {
    return rows.getObject(column, OffsetDateTime.class).atZoneSameInstant(ZoneOffset.UTC);
  }
}
