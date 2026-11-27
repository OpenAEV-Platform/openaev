package io.openaev.config;

import static io.openaev.config.StatementRedactor.UNTERMINATED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("StatementRedactor")
class StatementRedactorTest {

  private static final int ONE_MEGABYTE = 1024 * 1024;

  @Nested
  @DisplayName("on a statement")
  class OnAStatement {

    static Stream<Arguments> statements() {
      return Stream.of(
          Arguments.of("WHERE a = 'x' AND b = 'it''s'", "WHERE a = ? AND b = ?"),
          Arguments.of("WHERE a = E'it\\'s' AND b = e'\\\\'", "WHERE a = ? AND b = ?"),
          Arguments.of("WHERE a = E'it''s\\n'", "WHERE a = ?"),
          Arguments.of("SELECT typE'x'", "SELECT typE?"),
          Arguments.of("WHERE a = $$it's$$ AND b = $tag$x$$y$tag$", "WHERE a = ? AND b = ?"),
          Arguments.of("WHERE a = $1 AND b$c$ = 'x'", "WHERE a = $1 AND b$c$ = ?"),
          Arguments.of("SELECT a -- it's\nFROM t", "SELECT a  \nFROM t"),
          Arguments.of("SELECT a /* x /* 'y' */ 'z' */ FROM t", "SELECT a   FROM t"),
          Arguments.of(
              "SELECT \"it's\", \"a\"\"b\" FROM t WHERE c = 'x'",
              "SELECT \"it's\", \"a\"\"b\" FROM t WHERE c = ?"),
          Arguments.of("WHERE a = 'x", "WHERE a = " + UNTERMINATED),
          Arguments.of("WHERE a = E'x\\'", "WHERE a = " + UNTERMINATED),
          Arguments.of("WHERE a = $tag$x$other$", "WHERE a = " + UNTERMINATED),
          Arguments.of("WHERE a = 1 /* x /* y */ 'z'", "WHERE a = 1 " + UNTERMINATED),
          Arguments.of("SELECT \"a FROM t WHERE b = 'x'", "SELECT " + UNTERMINATED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("statements")
    @DisplayName("replaces literals and comments, and copies identifiers and parameters")
    void given_statement_should_redactItsLiteralsAndComments(String sql, String redacted) {
      // Act
      String out = StatementRedactor.redactStatement(sql);

      // Assert
      assertEquals(redacted, out);
    }

    static Stream<Arguments> oneMegabyteBodies() {
      String body = "x".repeat(ONE_MEGABYTE);
      return Stream.of(
          Arguments.of("'" + body + "'"),
          Arguments.of("E'" + "\\'".repeat(ONE_MEGABYTE / 2) + "'"),
          Arguments.of("$$" + body + "$$"),
          Arguments.of(
              "/*" + "/*".repeat(ONE_MEGABYTE / 2) + "*/".repeat(ONE_MEGABYTE / 2) + "*/"));
    }

    @ParameterizedTest
    @MethodSource("oneMegabyteBodies")
    @DisplayName("redacts a literal or a comment of 1 MB")
    void given_oneMegabyteBody_should_redactItWithoutOverflowing(String body) {
      // Act
      String out = StatementRedactor.redactStatement("SELECT " + body + " FROM t");

      // Assert
      assertEquals(body.startsWith("/*") ? "SELECT   FROM t" : "SELECT ? FROM t", out);
    }
  }

  @Nested
  @DisplayName("on an exception message")
  class OnAMessage {

    @Test
    @DisplayName("replaces the parser's quoted token as well as literals")
    void given_parserMessage_should_redactTheQuotedToken() {
      // Arrange
      String message =
          "Encountered: <S_CHAR_LITERAL> / \"\\'it\\'\\'s\\'\", at line 1; value 'x' or \"a\\\"b";

      // Act
      String out = StatementRedactor.redactMessage(message);

      // Assert
      assertEquals(
          "Encountered: <S_CHAR_LITERAL> / \"?\", at line 1; value ? or " + UNTERMINATED, out);
    }
  }
}
