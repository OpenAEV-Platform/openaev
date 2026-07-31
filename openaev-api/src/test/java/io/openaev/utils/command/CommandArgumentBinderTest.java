package io.openaev.utils.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Command argument binder")
class CommandArgumentBinderTest {

  /** Classic shell-injection payloads that must never alter the executed command structure. */
  private static Stream<String> maliciousValues() {
    return Stream.of(
        "; whoami",
        "&& whoami",
        "| whoami",
        "`whoami`",
        "$(whoami)",
        "$IFS",
        "%OS%",
        "!PATH!",
        "a\nwhoami",
        "a\r\nwhoami",
        "> /tmp/pwned",
        "' ; whoami ; '",
        "\" ; whoami ; \"",
        "a\u0000whoami");
  }

  @Nested
  @DisplayName("POSIX shells")
  class Sh {

    @ParameterizedTest
    @MethodSource("io.openaev.utils.command.CommandArgumentBinderTest#maliciousValues")
    @DisplayName("given a metacharacter-laden value should keep it inside a single-quoted literal")
    void given_malicious_value_should_quote_it(String value) {
      // -- ARRANGE --
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");

      // -- ACT --
      binder.bind("target", value);
      String rendered = binder.render("echo #{target}");

      // -- ASSERT --
      assertThat(rendered).startsWith("OAEV_ARG_TARGET='").contains("\necho \"$OAEV_ARG_TARGET\"");
      // The value never reaches the command line itself, only the quoted declaration.
      assertThat(rendered.substring(rendered.indexOf("\necho")))
          .isEqualTo("\necho \"$OAEV_ARG_TARGET\"");
    }

    @Test
    @DisplayName("given a value containing a single quote should escape it")
    void given_single_quote_should_escape_it() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("sh");

      binder.bind("a", "it's ok");

      assertThat(binder.render("echo #{a}")).startsWith("OAEV_ARG_A='it'\\''s ok'");
    }

    @Test
    @DisplayName("given a quoted placeholder should not double quote it")
    void given_quoted_placeholder_should_not_double_quote() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");

      binder.bind("a", "value");

      assertThat(binder.render("echo \"#{a}\"")).endsWith("echo \"$OAEV_ARG_A\"");
    }
  }

  @Nested
  @DisplayName("PowerShell")
  class PowerShell {

    @ParameterizedTest
    @MethodSource("io.openaev.utils.command.CommandArgumentBinderTest#maliciousValues")
    @DisplayName("given a metacharacter-laden value should keep it inside a single-quoted literal")
    void given_malicious_value_should_quote_it(String value) {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("psh");

      binder.bind("target", value);
      String rendered = binder.render("Write-Output #{target}");

      assertThat(rendered).startsWith("$OAEV_ARG_TARGET = '");
      assertThat(rendered).endsWith("Write-Output ${OAEV_ARG_TARGET}");
    }

    @Test
    @DisplayName("given a value containing a single quote should double it")
    void given_single_quote_should_double_it() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("powershell");

      binder.bind("a", "it's ok");

      assertThat(binder.render("echo #{a}")).startsWith("$OAEV_ARG_A = 'it''s ok'");
    }
  }

  @Nested
  @DisplayName("Windows cmd")
  class Cmd {

    @ParameterizedTest
    @MethodSource("io.openaev.utils.command.CommandArgumentBinderTest#maliciousValues")
    @DisplayName("given a metacharacter-laden value should keep the command on a single statement")
    void given_malicious_value_should_neutralize_it(String value) {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("cmd");

      binder.bind("target", value);
      String rendered = binder.render("echo #{target}");

      assertThat(rendered)
          .startsWith("set \"OAEV_ARG_TARGET=")
          .endsWith(" & echo \"!OAEV_ARG_TARGET!\"");
      // No newline may survive: cmd would treat it as a new statement.
      assertThat(rendered).doesNotContain("\n").doesNotContain("\r");
      // Delayed expansion can no longer be triggered from a value.
      String declaration = rendered.substring(0, rendered.indexOf(" & echo"));
      assertThat(declaration.replace("^^!", "")).doesNotContain("!");
    }

    @Test
    @DisplayName("given percent and bang variables should escape them in the declaration")
    void given_variable_syntax_should_escape_it() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("cmd");

      binder.bind("a", "%OS% !PATH!");

      assertThat(binder.render("echo #{a}")).startsWith("set \"OAEV_ARG_A=%%OS%% ^^!PATH^^!\"");
    }
  }

  @Nested
  @DisplayName("Non-shell context")
  class Literal {

    @Test
    @DisplayName("given a literal binder should substitute the value without declaring a variable")
    void given_literal_binder_should_substitute_value() {
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("host", "example.com");

      assertThat(binder.render("#{host}")).isEqualTo("example.com");
    }

    @Test
    @DisplayName("given a value with control characters should strip them")
    void given_control_characters_should_strip_them() {
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("host", "exa\u0000mple.com");

      assertThat(binder.render("#{host}")).isEqualTo("example.com");
    }
  }

  @Nested
  @DisplayName("Variable naming")
  class Naming {

    @Test
    @DisplayName("given keys colliding after sanitization should allocate distinct variables")
    void given_colliding_keys_should_allocate_distinct_variables() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");

      binder.bind("my-arg", "first");
      binder.bind("my.arg", "second");
      String rendered = binder.render("echo #{my-arg} #{my.arg}");

      assertThat(rendered)
          .contains("OAEV_ARG_MY_ARG='first'")
          .contains("OAEV_ARG_MY_ARG_1='second'");
      assertThat(rendered).endsWith("echo \"$OAEV_ARG_MY_ARG\" \"$OAEV_ARG_MY_ARG_1\"");
    }

    @Test
    @DisplayName("given a key referenced twice should declare it once")
    void given_repeated_key_should_declare_once() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");

      binder.bind("a", "value");
      String rendered = binder.render("echo #{a} #{a}");

      assertThat(rendered.split("OAEV_ARG_A=", -1)).hasSize(2);
      assertThat(rendered).endsWith("echo \"$OAEV_ARG_A\" \"$OAEV_ARG_A\"");
    }
  }
}
