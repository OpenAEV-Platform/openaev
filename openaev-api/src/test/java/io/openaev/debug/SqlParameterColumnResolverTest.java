package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SqlParameterColumnResolver")
class SqlParameterColumnResolverTest {

  @Test
  @DisplayName("maps INSERT columns positionally to placeholders")
  void insertPositional() {
    List<String> columns =
        SqlParameterColumnResolver.resolve(
            "insert into users (user_id, user_email, user_password) values (?, ?, ?)");

    assertThat(columns).containsExactly("user_id", "user_email", "user_password");
  }

  @Test
  @DisplayName("maps UPDATE assignments and WHERE comparisons to their column")
  void updateAndWhere() {
    List<String> columns =
        SqlParameterColumnResolver.resolve(
            "update users set user_password = ?, user_name = ? where user_id = ?");

    assertThat(columns).containsExactly("user_password", "user_name", "user_id");
  }

  @Test
  @DisplayName("strips table qualifier and quotes from column names")
  void qualifiedColumns() {
    List<String> columns =
        SqlParameterColumnResolver.resolve("select * from t where t.\"token\" = ?");

    assertThat(columns).containsExactly("token");
  }

  @Test
  @DisplayName("returns no mapping for statements without placeholders")
  void noPlaceholders() {
    assertThat(SqlParameterColumnResolver.resolve("select 1")).isEmpty();
    assertThat(SqlParameterColumnResolver.resolve(null)).isEmpty();
  }

  @Test
  @DisplayName("ignores a question mark inside a string literal")
  void questionMarkInsideLiteralIsNotAPlaceholder() {
    List<String> columns =
        SqlParameterColumnResolver.resolve("select id from t where label = 'a?b' and token = ?");

    assertThat(columns).containsExactly("token");
  }

  @Test
  @DisplayName("maps DELETE where comparisons")
  void deleteWhere() {
    List<String> columns =
        SqlParameterColumnResolver.resolve("delete from tokens where token_value = ?");

    assertThat(columns).containsExactly("token_value");
  }

  @Test
  @DisplayName("keeps one entry per placeholder even when a column cannot be resolved")
  void unresolvedPlaceholderKeepsAlignment() {
    List<String> columns =
        SqlParameterColumnResolver.resolve("select id from t where user_id = ? limit ?");

    assertThat(columns).hasSize(2);
    assertThat(columns.get(0)).isEqualTo("user_id");
    assertThat(columns.get(1)).isNull();
  }
}
