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
        // PowerShell also closes a single-quoted string on a typographic apostrophe.
        LEFT_SINGLE_QUOTE + " ; whoami ; " + LEFT_SINGLE_QUOTE,
        RIGHT_SINGLE_QUOTE + " ; whoami ; " + RIGHT_SINGLE_QUOTE,
        SINGLE_LOW_9_QUOTE + " ; whoami ; " + SINGLE_LOW_9_QUOTE,
        SINGLE_HIGH_REVERSED_9_QUOTE + " ; whoami ; " + SINGLE_HIGH_REVERSED_9_QUOTE,
        TYPOGRAPHIC_DOUBLE_QUOTES + " ; whoami ; " + TYPOGRAPHIC_DOUBLE_QUOTES,
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
        // Line separators beyond CR and LF, built from code points because a literal one in this
        // source file would break the string it sits in. Unicode designates these as line
        // separators; java.util.Scanner splits on them, String.lines() and BufferedReader do not.
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
   * The typographic apostrophes PowerShell reads as single-quote delimiters, as it does the ASCII
   * one (PowerShell language specification, section 2.3.5.2).
   */
  private static final String LEFT_SINGLE_QUOTE = "\u2018";

  private static final String RIGHT_SINGLE_QUOTE = "\u2019";
  private static final String SINGLE_LOW_9_QUOTE = "\u201A";
  private static final String SINGLE_HIGH_REVERSED_9_QUOTE = "\u201B";

  /** Every character PowerShell reads as a single-quote delimiter. */
  private static final String POWERSHELL_SINGLE_QUOTES =
      "'"
          + LEFT_SINGLE_QUOTE
          + RIGHT_SINGLE_QUOTE
          + SINGLE_LOW_9_QUOTE
          + SINGLE_HIGH_REVERSED_9_QUOTE;

  /**
   * The typographic double quotes PowerShell reads as double-quote delimiters. They mean nothing
   * inside a single-quoted string, nor to the other engines.
   */
  private static final String TYPOGRAPHIC_DOUBLE_QUOTES = "\u201C\u201D\u201E";

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
              + POWERSHELL_SINGLE_QUOTES
              + TYPOGRAPHIC_DOUBLE_QUOTES
              + "abcXY01")
          .toCharArray();

  /**
   * Characters a consumer may read as the end of a line. A value carrying one is not the single
   * token the template describes, whatever the shell does with it.
   */
  private static final String[] LINE_SEPARATORS = {
    "\n", "\r", NEL, LINE_SEPARATOR, PARAGRAPH_SEPARATOR
  };

  /** Asserts a value, once bound, carries no line separator into whatever the engine produced. */
  private static void assertNoLineSeparator(String subject, String context, String what) {
    for (String separator : LINE_SEPARATORS) {
      assertThat(subject)
          .as("%s must leave no line separator in %s", context, what)
          .doesNotContain(separator);
    }
  }

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
   * @return true when the value was accepted and rendered, false when the binder refused it
   */
  private static boolean assertSingleQuotedContainment(
      String executor,
      String template,
      String expectedTail,
      String assignment,
      String quoteEscape,
      String value) {
    CommandArgumentBinder binder = CommandArgumentBinder.forExecutor(executor);
    if (isRefused(binder, value)) {
      return false;
    }
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
    assertNoLineSeparator(interior, context, "its declaration");
    return true;
  }

  private static boolean assertShContainment(String value) {
    return assertSingleQuotedContainment(
        "bash", "echo #{target}", "echo \"$OAEV_ARG_TARGET\"", "OAEV_ARG_TARGET=", "'\\''", value);
  }

  /**
   * The invariant is a disjunction: a value the engine cannot carry must be refused outright, and
   * any value that is accepted must be rendered fully contained. This encodes neither which values
   * are refused nor why, so the assertion stays independent of the rule under test.
   *
   * @return true when the binder refused the value, in which case the refusal must name the
   *     argument so the caller can act on it
   */
  private static boolean isRefused(CommandArgumentBinder binder, String value) {
    try {
      binder.bind("target", value);
      return false;
    } catch (CommandBindingException refused) {
      assertThat(refused)
          .as("a refusal must name the argument it is about")
          .hasMessageContaining("target");
      return true;
    }
  }

  /**
   * Asserts the structural invariant for the cmd engine, whose declaration is {@code set
   * "VAR=value"}. The quoted region opened by {@code set "} ends at the next {@code "}, so the
   * value must contribute no quote of its own. Percent and delayed expansion must be neutralised
   * for the same reason: both would let the value name something outside itself.
   */
  private static boolean assertCmdContainment(String value) {
    CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("cmd");
    if (isRefused(binder, value)) {
      return false;
    }
    String rendered = binder.render("echo #{target}");
    String expectedTail = "echo \"!OAEV_ARG_TARGET!\"";
    String context = "value " + readable(value);

    assertThat(rendered).as("%s must not alter the command", context).endsWith(expectedTail);
    assertThat(rendered.indexOf(expectedTail))
        .as("%s must not smuggle a second copy of the command", context)
        .isEqualTo(rendered.length() - expectedTail.length());

    String opening = "set \"OAEV_ARG_TARGET=";
    String closing = "\" & ";
    String prologue = rendered.substring(0, rendered.length() - expectedTail.length());
    assertThat(prologue)
        .as("%s must stay inside its declaration", context)
        .startsWith(opening)
        .endsWith(closing);

    String interior = prologue.substring(opening.length(), prologue.length() - closing.length());
    assertThat(interior)
        .as("%s must not close its declaration early", context)
        .doesNotContain("\"");
    assertThat(interior.replace("^^!", ""))
        .as("%s must not leave delayed expansion reachable", context)
        .doesNotContain("!");
    assertThat(interior.replace("%%", ""))
        .as("%s must not leave percent expansion reachable", context)
        .doesNotContain("%");
    assertNoLineSeparator(interior, context, "its declaration");
    return true;
  }

  /** A single-quoted PowerShell string literal: its content and the index right after it. */
  private record PowerShellLiteral(String content, int end) {}

  /**
   * Reads the single-quoted string literal opening at {@code start} as PowerShell's tokenizer does:
   * any of the {@link #POWERSHELL_SINGLE_QUOTES} opens and closes it, whichever one opened it, and
   * two of them in a row stand for the second one. Unlike a search for the ASCII apostrophe, this
   * finds where PowerShell itself ends the string.
   */
  private static PowerShellLiteral readPowerShellLiteral(String source, int start) {
    assertThat(isPowerShellSingleQuote(source.charAt(start)))
        .as("a single-quoted string must open at index %d of %s", start, readable(source))
        .isTrue();
    StringBuilder content = new StringBuilder();
    int index = start + 1;
    while (index < source.length()) {
      char current = source.charAt(index);
      if (isPowerShellSingleQuote(current)) {
        boolean doubled =
            index + 1 < source.length() && isPowerShellSingleQuote(source.charAt(index + 1));
        if (!doubled) {
          return new PowerShellLiteral(content.toString(), index + 1);
        }
        content.append(source.charAt(index + 1));
        index += 2;
        continue;
      }
      content.append(current);
      index++;
    }
    throw new AssertionError("unterminated single-quoted string in " + readable(source));
  }

  private static boolean isPowerShellSingleQuote(char c) {
    return POWERSHELL_SINGLE_QUOTES.indexOf(c) >= 0;
  }

  private static long occurrences(String subject, char c) {
    return subject.chars().filter(current -> current == c).count();
  }

  /**
   * Asserts the structural invariant for PowerShell. The declaration is read with {@link
   * #readPowerShellLiteral}, so a typographic apostrophe closing the string early is caught, and
   * the literal must end exactly where the declaration line does. Every quote of the value must
   * also reach the literal as itself: doubling with the ASCII apostrophe would keep the string
   * closed but change the value.
   */
  private static boolean assertPowerShellContainment(String value) {
    CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("psh");
    if (isRefused(binder, value)) {
      return false;
    }
    String rendered = binder.render("Write-Output #{target}");
    String expectedTail = "Write-Output ${OAEV_ARG_TARGET}";
    String context = "value " + readable(value);

    assertThat(rendered).as("%s must not alter the command", context).endsWith(expectedTail);
    assertThat(rendered.indexOf(expectedTail))
        .as("%s must not smuggle a second copy of the command", context)
        .isEqualTo(rendered.length() - expectedTail.length());

    String assignment = "$OAEV_ARG_TARGET = ";
    String prologue = rendered.substring(0, rendered.length() - expectedTail.length());
    assertThat(prologue)
        .as("%s must stay inside its declaration", context)
        .startsWith(assignment)
        .endsWith("\n");

    PowerShellLiteral literal = readPowerShellLiteral(prologue, assignment.length());
    assertThat(literal.end())
        .as("%s must not close its declaration early", context)
        .isEqualTo(prologue.length() - "\n".length());
    for (char quote : POWERSHELL_SINGLE_QUOTES.toCharArray()) {
      assertThat(occurrences(literal.content(), quote))
          .as("%s must keep every %s as itself", context, readable(String.valueOf(quote)))
          .isEqualTo(occurrences(value, quote));
    }
    assertNoLineSeparator(literal.content(), context, "its declaration");
    return true;
  }

  /** The declared value, as PowerShell reads it back from the first line of the rendering. */
  private static String powerShellDeclaredValue(String rendered) {
    String assignment = "$OAEV_ARG_A = ";
    String declaration = rendered.substring(0, rendered.indexOf('\n'));
    assertThat(declaration).startsWith(assignment);
    PowerShellLiteral literal = readPowerShellLiteral(declaration, assignment.length());
    assertThat(literal.end())
        .as("the declaration %s must be one single-quoted string", readable(declaration))
        .isEqualTo(declaration.length());
    return literal.content();
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
    @DisplayName("given a value carrying a double quote should still be accepted")
    void given_double_quote_should_be_accepted() {
      // The cmd restriction must not leak into shells that can quote a double quote.
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");

      binder.bind("a", "say \"hello\"");

      assertThat(binder.render("echo #{a}")).startsWith("OAEV_ARG_A='say \"hello\"'");
    }

    @Test
    @DisplayName("given a value containing a single quote should escape it")
    void given_single_quote_should_escape_it() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("sh");

      binder.bind("a", "it's ok");

      assertThat(binder.render("echo #{a}")).startsWith("OAEV_ARG_A='it'\\''s ok'");
    }

    @Test
    @DisplayName("given typographic quotes should keep them as plain data")
    void given_typographic_quotes_should_keep_them_as_data() {
      // POSIX shells delimit with the ASCII apostrophe alone: nothing to escape here.
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");
      String value = POWERSHELL_SINGLE_QUOTES.substring(1) + TYPOGRAPHIC_DOUBLE_QUOTES;

      binder.bind("a", value);

      assertThat(binder.render("echo #{a}")).startsWith("OAEV_ARG_A='" + value + "'\n");
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
    @DisplayName("given a value carrying a double quote should still be accepted")
    void given_double_quote_should_be_accepted() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("psh");

      binder.bind("a", "say \"hello\"");

      assertThat(binder.render("echo #{a}")).startsWith("$OAEV_ARG_A = 'say \"hello\"'");
    }

    @Test
    @DisplayName("given a value containing a single quote should double it")
    void given_single_quote_should_double_it() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("powershell");

      binder.bind("a", "it's ok");

      assertThat(binder.render("echo #{a}")).startsWith("$OAEV_ARG_A = 'it''s ok'");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          LEFT_SINGLE_QUOTE,
          RIGHT_SINGLE_QUOTE,
          SINGLE_LOW_9_QUOTE,
          SINGLE_HIGH_REVERSED_9_QUOTE
        })
    @DisplayName("given a typographic apostrophe should double it with itself")
    void given_typographic_apostrophe_should_double_it(String apostrophe) {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("powershell");

      binder.bind("a", "it" + apostrophe + "s ok");

      String rendered = binder.render("echo #{a}");
      assertThat(rendered).startsWith("$OAEV_ARG_A = 'it" + apostrophe + apostrophe + "s ok'\n");
      assertThat(powerShellDeclaredValue(rendered)).isEqualTo("it" + apostrophe + "s ok");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          LEFT_SINGLE_QUOTE,
          RIGHT_SINGLE_QUOTE,
          SINGLE_LOW_9_QUOTE,
          SINGLE_HIGH_REVERSED_9_QUOTE
        })
    @DisplayName("given a typographic apostrophe closing the string should keep the rest as data")
    void given_typographic_apostrophe_breakout_should_stay_data(String apostrophe) {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("pwsh");
      String value = apostrophe + "; Start-Process calc; " + apostrophe;

      binder.bind("a", value);

      String rendered = binder.render("Write-Output #{a}");
      assertThat(powerShellDeclaredValue(rendered)).isEqualTo(value);
      assertThat(rendered).endsWith("\nWrite-Output ${OAEV_ARG_A}");
    }

    @Test
    @DisplayName("given ASCII and typographic apostrophes mixed should double each with itself")
    void given_mixed_apostrophes_should_double_each_with_itself() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("psh");
      String value =
          "a'b"
              + LEFT_SINGLE_QUOTE
              + "c"
              + RIGHT_SINGLE_QUOTE
              + "d"
              + SINGLE_LOW_9_QUOTE
              + "e"
              + SINGLE_HIGH_REVERSED_9_QUOTE
              + "f''"
              + RIGHT_SINGLE_QUOTE
              + LEFT_SINGLE_QUOTE
              + TYPOGRAPHIC_DOUBLE_QUOTES;

      binder.bind("a", value);

      String rendered = binder.render("echo #{a}");
      assertThat(rendered)
          .startsWith(
              "$OAEV_ARG_A = 'a''b"
                  + LEFT_SINGLE_QUOTE.repeat(2)
                  + "c"
                  + RIGHT_SINGLE_QUOTE.repeat(2)
                  + "d"
                  + SINGLE_LOW_9_QUOTE.repeat(2)
                  + "e"
                  + SINGLE_HIGH_REVERSED_9_QUOTE.repeat(2)
                  + "f''''"
                  + RIGHT_SINGLE_QUOTE.repeat(2)
                  + LEFT_SINGLE_QUOTE.repeat(2)
                  + TYPOGRAPHIC_DOUBLE_QUOTES
                  + "'\n");
      assertThat(powerShellDeclaredValue(rendered)).isEqualTo(value);
    }

    @Test
    @DisplayName("given a value made only of apostrophes should still be one string")
    void given_only_apostrophes_should_still_be_one_string() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("psh");
      String value = POWERSHELL_SINGLE_QUOTES + POWERSHELL_SINGLE_QUOTES;

      binder.bind("a", value);

      assertThat(powerShellDeclaredValue(binder.render("echo #{a}"))).isEqualTo(value);
    }

    @Test
    @DisplayName("the test tokenizer ends a string on a lone typographic apostrophe")
    void the_test_tokenizer_ends_a_string_on_a_typographic_apostrophe() {
      // Guards the oracle itself: an apostrophe left undoubled must read as closing the string.
      String unescaped =
          "$OAEV_ARG_A = '" + RIGHT_SINGLE_QUOTE + "; calc; " + RIGHT_SINGLE_QUOTE + "'";

      PowerShellLiteral literal = readPowerShellLiteral(unescaped, "$OAEV_ARG_A = ".length());

      assertThat(literal.content()).isEmpty();
      assertThat(literal.end()).isLessThan(unescaped.length());
    }
  }

  @Nested
  @DisplayName("Windows cmd")
  class Cmd {

    @ParameterizedTest
    @MethodSource("io.openaev.utils.command.CommandArgumentBinderTest#maliciousValues")
    @DisplayName("given a metacharacter-laden value should keep it inside its own declaration")
    void given_malicious_value_should_neutralize_it(String value) {
      assertCmdContainment(value);
    }

    @Test
    @DisplayName("property: no generated value alters the command, over many random inputs")
    void property_no_generated_value_alters_the_command() {
      Random random = new Random(20260812L);
      int accepted = 0;

      for (int i = 0; i < GENERATED_SAMPLES; i++) {
        if (assertCmdContainment(hostileValue(random))) {
          accepted++;
        }
      }

      // The invariant above is a disjunction, so a binder that refused everything would satisfy
      // it. This pins the other side: the overwhelming majority of hostile values are still
      // rendered, and only the ones cmd cannot carry are turned away.
      assertThat(accepted)
          .as("refusing everything would trivially satisfy the containment property")
          .isGreaterThan(GENERATED_SAMPLES / 2);
    }

    @Test
    @DisplayName("given a value carrying a double quote should refuse it and name the argument")
    void given_unrepresentable_value_should_refuse_and_name_the_argument() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("cmd");

      assertThatThrownBy(() -> binder.bind("guest_user", "x\" & calc"))
          .isInstanceOf(CommandBindingException.class)
          .hasMessageContaining("guest_user")
          .hasMessageContaining("cmd");
    }

    @Test
    @DisplayName("given a benign value should still render, so the refusal stays narrow")
    void given_benign_value_should_still_render() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("cmd");

      binder.bind("a", "C:\\Program Files\\app.exe");

      assertThat(binder.render("run #{a}"))
          .isEqualTo("set \"OAEV_ARG_A=C:\\Program Files\\app.exe\" & run \"!OAEV_ARG_A!\"");
    }

    @Test
    @DisplayName("given typographic quotes should accept them as plain data")
    void given_typographic_quotes_should_accept_them_as_data() {
      // cmd delimits with the ASCII double quote alone: a typographic one is neither refused nor
      // escaped.
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("cmd");
      String value = POWERSHELL_SINGLE_QUOTES + TYPOGRAPHIC_DOUBLE_QUOTES;

      binder.bind("a", value);

      assertThat(binder.render("run #{a}"))
          .isEqualTo("set \"OAEV_ARG_A=" + value + "\" & run \"!OAEV_ARG_A!\"");
    }

    @Test
    @DisplayName("given a placeholder inside a wider quoted string should render it unquoted")
    void given_placeholder_inside_a_wider_quoted_string_should_render_it_unquoted() {
      // The class javadoc tells template authors not to quote a placeholder themselves. A
      // placeholder sitting inside a wider quoted string is not that case and is not detected, so
      // the reference lands outside the template's quotes. Pinned because it is the shape the
      // containment tests do not exercise: they all use a bare placeholder.
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("cmd");

      binder.bind("t", "a b");

      assertThat(binder.render("echo \"prefix #{t} suffix\""))
          .isEqualTo("set \"OAEV_ARG_T=a b\" & echo \"prefix \"!OAEV_ARG_T!\" suffix\"");
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
    @DisplayName("given a multi-line value should resolve a single hostname")
    void given_multi_line_value_should_resolve_a_single_hostname() {
      // The implant resolves a hostname line by line, so a value carrying a line separator would
      // resolve several names instead of the one the template describes.
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("host", "example.com\nattacker.test");

      assertThat(binder.render("#{host}")).isEqualTo("example.comattacker.test").hasLineCount(1);
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
      // No declaration here, so the whole rendered value must be a single line: the implant
      // resolves a DNS hostname line by line.
      assertNoLineSeparator(rendered, "value " + readable(value), "the rendered hostname");
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
  @DisplayName("Behaviour pinned as it stands")
  class PinnedBehaviour {

    @Test
    @DisplayName("a null template renders as null")
    void a_null_template_renders_as_null() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");
      binder.bind("a", "value");

      assertThat(binder.render(null)).isNull();
    }

    @Test
    @DisplayName("a null value binds as empty rather than failing")
    void a_null_value_binds_as_empty() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");

      binder.bind("a", null);

      assertThat(binder.render("echo #{a}")).isEqualTo("OAEV_ARG_A=''\necho \"$OAEV_ARG_A\"");
    }

    @Test
    @DisplayName("a placeholder inside a wider quoted string is substituted, not left alone")
    void a_placeholder_inside_a_wider_quoted_string_is_substituted() {
      // The authoring rule tells template authors not to do this. Pinned because the outcome is
      // easy to misread: the command is structurally safe, but under sh the reference sits inside
      // the template's own single quotes and never expands, so the command sees the variable name.
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");

      binder.bind("x", "value");

      assertThat(binder.render("echo 'prefix #{x} suffix'"))
          .endsWith("echo 'prefix \"$OAEV_ARG_X\" suffix'");
    }

    @Test
    @DisplayName("a template with no placeholder is returned untouched")
    void a_template_with_no_placeholder_is_returned_untouched() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");

      assertThat(binder.render("echo hello")).isEqualTo("echo hello");
    }

    @Test
    @DisplayName("binding the same key twice keeps the first value")
    void binding_the_same_key_twice_keeps_the_first_value() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");

      binder.bind("a", "first");
      binder.bind("a", "second");

      assertThat(binder.render("echo #{a}")).startsWith("OAEV_ARG_A='first'");
    }
  }

  @Nested
  @DisplayName("Substitution passes")
  class SubstitutionPasses {

    @Test
    @DisplayName("a value shaped like another placeholder is not expanded")
    void a_value_shaped_like_another_placeholder_is_not_expanded() {
      // Substitution must read the template once. A value inserted by one key must never be
      // re-examined for placeholders belonging to another.
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("a", "#{b}");
      binder.bind("b", "resolved");

      assertThat(binder.render("host-#{a}-#{b}")).isEqualTo("host-#{b}-resolved");
    }

    @Test
    @DisplayName("the outcome does not depend on the order the arguments were bound")
    void the_outcome_does_not_depend_on_bind_order() {
      CommandArgumentBinder first = CommandArgumentBinder.literal();
      first.bind("a", "#{b}");
      first.bind("b", "resolved");

      CommandArgumentBinder second = CommandArgumentBinder.literal();
      second.bind("b", "resolved");
      second.bind("a", "#{b}");

      assertThat(second.render("host-#{a}-#{b}")).isEqualTo(first.render("host-#{a}-#{b}"));
    }

    @Test
    @DisplayName("a value shaped like its own placeholder is not expanded either")
    void a_value_shaped_like_its_own_placeholder_is_not_expanded() {
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("a", "#{a}");

      assertThat(binder.render("host-#{a}")).isEqualTo("host-#{a}");
    }

    @Test
    @DisplayName("a key that is a prefix of another still resolves to its own value")
    void a_key_that_is_a_prefix_of_another_resolves_correctly() {
      // Matching every key in one pass means they share an alternation, where a shorter key could
      // shadow a longer one if the engine did not backtrack.
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("a", "SHORT");
      binder.bind("ab", "LONG");

      assertThat(binder.render("#{a}|#{ab}")).isEqualTo("SHORT|LONG");
    }

    @Test
    @DisplayName("the longer key resolves the same whichever order it was bound in")
    void the_longer_key_resolves_the_same_whichever_order() {
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("ab", "LONG");
      binder.bind("a", "SHORT");

      assertThat(binder.render("#{a}|#{ab}")).isEqualTo("SHORT|LONG");
    }

    @Test
    @DisplayName("a key carrying regex metacharacters is matched literally")
    void a_key_carrying_regex_metacharacters_is_matched_literally() {
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("a.b", "DOT");
      binder.bind("axb", "NOT_DOT");

      assertThat(binder.render("#{a.b}|#{axb}")).isEqualTo("DOT|NOT_DOT");
    }

    @Test
    @DisplayName("a key metacharacter must not match an unbound placeholder")
    void a_key_metacharacter_must_not_match_an_unbound_placeholder() {
      // If keys were pasted into the pattern unquoted, the dot in this key would match any
      // character and swallow a placeholder that was never bound.
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("a.b", "DOT");

      assertThat(binder.render("#{a.b}|#{axb}")).isEqualTo("DOT|#{axb}");
    }

    @Test
    @DisplayName("a key carrying an alternation character stays a single key")
    void a_key_carrying_an_alternation_character_stays_one_key() {
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("a|b", "PIPED");

      assertThat(binder.render("#{a|b}|#{a}")).isEqualTo("PIPED|#{a}");
    }

    @Test
    @DisplayName("an unbound placeholder is left untouched")
    void an_unbound_placeholder_is_left_untouched() {
      CommandArgumentBinder binder = CommandArgumentBinder.literal();

      binder.bind("a", "resolved");

      assertThat(binder.render("#{a}|#{never_bound}")).isEqualTo("resolved|#{never_bound}");
    }

    @Test
    @DisplayName("binding modes were never exposed, the value goes to the declaration")
    void binding_modes_keep_the_value_out_of_the_template() {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor("bash");

      binder.bind("a", "#{b}");
      binder.bind("b", "resolved");

      assertThat(binder.render("echo #{a} #{b}"))
          .endsWith("echo \"$OAEV_ARG_A\" \"$OAEV_ARG_B\"")
          .contains("OAEV_ARG_A='#{b}'");
    }
  }

  @Nested
  @DisplayName("Characters deliberately kept")
  class KeptCharacters {

    @Test
    @DisplayName("tab survives on every engine, it separates nothing in any supported shell")
    void tab_survives_on_every_engine() {
      assertThat(rendered("bash", "a\tb")).contains("a\tb");
      assertThat(rendered("psh", "a\tb")).contains("a\tb");
      assertThat(rendered("cmd", "a\tb")).contains("a\tb");
      assertThat(literalRendered("a\tb")).contains("a\tb");
    }

    @Test
    @DisplayName("a byte order mark survives, it is not a line separator")
    void byte_order_mark_survives() {
      // Kept on purpose: BOM is a zero-width no-break space, not a line separator. Removing
      // invisible characters is a different concern from the one this class owns.
      String value = "a" + BYTE_ORDER_MARK + "b";

      assertThat(rendered("bash", value)).contains(value);
      assertThat(literalRendered(value)).contains(value);
    }

    private String rendered(String executor, String value) {
      CommandArgumentBinder binder = CommandArgumentBinder.forExecutor(executor);
      binder.bind("t", value);
      return binder.render("echo #{t}");
    }

    private String literalRendered(String value) {
      CommandArgumentBinder binder = CommandArgumentBinder.literal();
      binder.bind("t", value);
      return binder.render("host-#{t}");
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
