package io.openaev.utils.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
        // A value carrying the cmd statement separator: a test locating the prologue by searching
        // for that separator would land inside the value instead of at the real boundary.
        "a & whoami",
        // Escape characters that must stay data rather than becoming escapes of their own.
        "a^b",
        "a^^b",
        "trailing caret ^",
        "trailing backslash \\",
        "a ( b ) c",
        "a\tb",
        // Line terminators beyond CR and LF, built from code points because a literal one in
        // this source file would break the string it sits in. Java treats NEL, LS and PS as line
        // terminators, so a value carrying one is not the single line it looks like.
        "a" + NEL + "b",
        "a" + LINE_SEPARATOR + "b",
        "a" + PARAGRAPH_SEPARATOR + "b",
        "a" + BYTE_ORDER_MARK + "b",
        // A value shaped like a placeholder, so a substitution pass cannot mistake it for one.
        "#{target}",
        "#{other}",
        "a\u0000whoami");
  }

  private static final String NEL = Character.toString(0x0085);
  private static final String LINE_SEPARATOR = Character.toString(0x2028);
  private static final String PARAGRAPH_SEPARATOR = Character.toString(0x2029);
  private static final String BYTE_ORDER_MARK = Character.toString(0xFEFF);

  /**
   * Characters a hostile value is built from: every metacharacter, escape, quote, separator and
   * line terminator the three engines give meaning to, plus filler.
   */
  private static final char[] HOSTILE_ALPHABET =
      ("'\"\\^!%$&|<>();`#{}=*?[] \t\r\n"
              + NEL
              + LINE_SEPARATOR
              + PARAGRAPH_SEPARATOR
              + BYTE_ORDER_MARK
              + "abcXY01")
          .toCharArray();

  /** How many generated values each property test samples. */
  private static final int GENERATED_SAMPLES = 20_000;

  /**
   * Asserts the structural invariant for an engine that declares values in a single-quoted string:
   * the rendered command is the binder's prologue followed by the template with its placeholders
   * replaced by references, and the value contributes only to the interior of its own declaration.
   *
   * <p>The prologue boundary is derived from the expected tail, never by searching for the
   * statement separator, because a value may itself contain that separator.
   *
   * @param quoteEscape how the engine escapes a single quote inside a single-quoted string
   */
  private static void assertSingleQuotedContainment(
      String executor,
      String template,
      String expectedTail,
      String assignment,
      String quoteEscape,
      String value) {
    CommandArgumentBinder binder = CommandArgumentBinder.forExecutor(executor);
    binder.bind("target", value);
    String rendered = binder.render(template);
    String context = "value " + readable(value);

    assertThat(rendered).as("%s must not alter the command", context).endsWith(expectedTail);
    assertThat(rendered.indexOf(expectedTail))
        .as("%s must not smuggle a second copy of the command", context)
        .isEqualTo(rendered.length() - expectedTail.length());

    String prologue = rendered.substring(0, rendered.length() - expectedTail.length());
    assertThat(prologue)
        .as("%s must stay inside its declaration", context)
        .startsWith(assignment + "'")
        .endsWith("'\n");

    String interior =
        prologue.substring(assignment.length() + 1, prologue.length() - "'\n".length());
    assertThat(interior.replace(quoteEscape, ""))
        .as("%s must not close its declaration early", context)
        .doesNotContain("'");
  }

  private static void assertShContainment(String value) {
    assertSingleQuotedContainment(
        "bash", "echo #{target}", "echo \"$OAEV_ARG_TARGET\"", "OAEV_ARG_TARGET=", "'\\''", value);
  }

  private static void assertPowerShellContainment(String value) {
    assertSingleQuotedContainment(
        "psh",
        "Write-Output #{target}",
        "Write-Output ${OAEV_ARG_TARGET}",
        "$OAEV_ARG_TARGET = ",
        "''",
        value);
  }

  /**
   * Generates a value from {@link #HOSTILE_ALPHABET}. The seed is fixed by the caller so a failing
   * build names a value that can be reproduced exactly, rather than a different one on every run.
   */
  private static String hostileValue(Random random) {
    int length = random.nextInt(14);
    StringBuilder value = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      value.append(HOSTILE_ALPHABET[random.nextInt(HOSTILE_ALPHABET.length)]);
    }
    return value.toString();
  }

  /** Renders control and non-ASCII characters visible, so an assertion message is actionable. */
  private static String readable(String value) {
    StringBuilder out = new StringBuilder();
    value
        .chars()
        .forEach(c -> out.append(c < 0x20 || c > 0x7e ? "\\u%04x".formatted(c) : (char) c));
    return out.toString();
  }

  @Nested
  @DisplayName("POSIX shells")
  class Sh {

    @ParameterizedTest
    @MethodSource("io.openaev.utils.command.CommandArgumentBinderTest#maliciousValues")
    @DisplayName("given a metacharacter-laden value should keep it inside its own declaration")
    void given_malicious_value_should_quote_it(String value) {
      assertShContainment(value);
    }

    @Test
    @DisplayName("property: no generated value alters the command, over many random inputs")
    void property_no_generated_value_alters_the_command() {
      // A fixture list only proves the binder handles the payloads someone thought of. This
      // asserts the invariant itself, over values built from every character the engines give
      // meaning to. The seed is fixed so a failure names a reproducible value.
      Random random = new Random(20260812L);

      for (int i = 0; i < GENERATED_SAMPLES; i++) {
        assertShContainment(hostileValue(random));
      }
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
    @DisplayName("given a metacharacter-laden value should keep it inside its own declaration")
    void given_malicious_value_should_quote_it(String value) {
      assertPowerShellContainment(value);
    }

    @Test
    @DisplayName("property: no generated value alters the command, over many random inputs")
    void property_no_generated_value_alters_the_command() {
      Random random = new Random(20260812L);

      for (int i = 0; i < GENERATED_SAMPLES; i++) {
        assertPowerShellContainment(hostileValue(random));
      }
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

    @Test
    @DisplayName("given a quoted placeholder should keep the template quotes untouched")
    void given_quoted_placeholder_should_keep_template_quotes() {
      // Arrange: literal mode powers the read-only terminal view, which must mirror the template.
      CommandArgumentBinder binder = CommandArgumentBinder.literal();
      binder.bind("host", "localhost");
      binder.bind("port", "22");

      // Act
      String rendered = binder.render("echo \"#{host}\":'#{port}'");

      // Assert
      assertThat(rendered).isEqualTo("echo \"localhost\":'22'");
    }

    @ParameterizedTest
    @MethodSource("io.openaev.utils.command.CommandArgumentBinderTest#maliciousValues")
    @DisplayName("given any value should substitute verbatim and never declare a variable")
    void given_any_value_should_substitute_without_declaring(String value) {
      // Literal mode backs the read-only display and the DNS hostname, which never reach a shell.
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("target", value);
      String rendered = binder.render("host-#{target}");

      assertThat(rendered).startsWith("host-").doesNotContain("OAEV_");
    }

    @Test
    @DisplayName("given a literal binder should never prepend a variable declaration")
    void given_literal_binder_should_not_prepend_declaration() {
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("host", "a; whoami");

      assertThat(binder.render("echo #{host}")).isEqualTo("echo a; whoami").doesNotContain("OAEV_");
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

  @Nested
  @DisplayName("Unsupported executor (fail closed)")
  class UnsupportedExecutor {

    @ParameterizedTest
    @ValueSource(strings = {"python", "perl", "bash5", "unknown"})
    @DisplayName("given an unmapped executor and a bound argument should refuse to render")
    void given_unmappedExecutorWithArgument_should_throw(String executor) {
      // Arrange
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor(executor);
      binder.bind("host", "a; whoami");

      // Act + Assert
      assertThatThrownBy(() -> binder.render("echo #{host}"))
          .isInstanceOf(CommandBindingException.class)
          .hasMessageContaining(executor);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("given a null or blank executor and a bound argument should refuse to render")
    void given_blankExecutorWithArgument_should_throw(String executor) {
      // Arrange
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor(executor);
      binder.bind("host", "a; whoami");

      // Act + Assert
      assertThatThrownBy(() -> binder.render("echo #{host}"))
          .isInstanceOf(CommandBindingException.class);
    }

    @Test
    @DisplayName("given an unmapped executor without any argument should render unchanged")
    void given_unmappedExecutorWithoutArgument_should_render() {
      // Arrange
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("python");

      // Act + Assert
      assertThat(binder.render("print('hello')")).isEqualTo("print('hello')");
    }

    @Test
    @DisplayName("given the explicit literal binder should still substitute placeholders")
    void given_literalBinder_should_stillSubstitute() {
      // Arrange
      CommandArgumentBinder binder = CommandArgumentBinder.literal();
      binder.bind("host", "localhost");

      // Act + Assert
      assertThat(binder.render("echo #{host}")).isEqualTo("echo localhost");
    }
  }
}
