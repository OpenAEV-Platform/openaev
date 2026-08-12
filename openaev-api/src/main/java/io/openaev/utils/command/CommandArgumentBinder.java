package io.openaev.utils.command;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Binds inject argument values to shell variables instead of substituting them verbatim into a
 * command template.
 *
 * <p>This is the command-line equivalent of a SQL prepared statement: the value is declared once,
 * in a fully quoted/escaped declaration (the "prologue"), and the template only ever sees a
 * variable reference. Shell metacharacters carried by an argument value therefore cannot alter the
 * structure of the command that finally runs on the endpoint.
 *
 * <p>Rendered shape, for {@code echo #{target}} with {@code target = "a; whoami"}:
 *
 * <ul>
 *   <li>sh/bash/zsh — {@code OAEV_ARG_TARGET='a; whoami'\necho "$OAEV_ARG_TARGET"}
 *   <li>powershell — {@code $OAEV_ARG_TARGET = 'a; whoami'\necho ${OAEV_ARG_TARGET}}
 *   <li>cmd — {@code set "OAEV_ARG_TARGET=a; whoami" & echo "!OAEV_ARG_TARGET!"}
 * </ul>
 *
 * <p><b>Template authoring rule</b>: do not wrap a placeholder in quotes yourself ({@code
 * "#{arg}"}). The binder owns the quoting; directly adjacent quotes around a placeholder are
 * detected and removed, but a placeholder embedded in a wider single-quoted literal cannot be
 * resolved (it stays literal — safe, but not substituted).
 *
 * <p>Not thread-safe: create one instance per rendered command.
 */
@Slf4j
public class CommandArgumentBinder {

  /**
   * Prefix of every generated variable, namespaced to avoid clashing with the payload's own vars.
   */
  private static final String VARIABLE_PREFIX = "OAEV_ARG_";

  private static final Pattern NON_IDENTIFIER_CHARS = Pattern.compile("[^A-Za-z0-9_]");

  /**
   * Characters no legitimate argument value needs: NUL and the other C0 controls, DEL, and every
   * character a consumer may read as the end of a line. Line separators are removed for all engines
   * so a value is the single token the template describes, whatever reads it afterwards. Tab is
   * deliberately kept: it separates nothing in any of the supported shells.
   */
  private static final Pattern NUL_AND_CONTROL_CHARS =
      Pattern.compile("[\\u0000-\\u0008\\u000A-\\u001F\\u007F\\u0085\\u2028\\u2029]");

  private final ExecutorShell shell;

  /**
   * {@code true} when {@link ExecutorShell#NONE} was <em>suffered</em> (unknown executor) rather
   * than <em>chosen</em> ({@link #literal()}). Falling back to verbatim substitution on a command
   * line would reintroduce command injection, so rendering is refused instead.
   */
  private final boolean rejectPlaceholders;

  /** Executor this binder was built for, kept for diagnostics only. */
  private final String executor;

  /** argument key -> generated variable name, in declaration order. */
  private final Map<String, String> variablesByKey = new LinkedHashMap<>();

  /** generated variable name -> already escaped declaration value. */
  private final Map<String, String> valuesByVariable = new LinkedHashMap<>();

  private CommandArgumentBinder(ExecutorShell shell, boolean rejectPlaceholders, String executor) {
    this.shell = shell;
    this.rejectPlaceholders = rejectPlaceholders;
    this.executor = executor;
  }

  /**
   * Binder for a command that will actually be executed on an endpoint.
   *
   * <p>If no binding strategy exists for the executor, the binder is built in <em>fail-closed</em>
   * mode: a template referencing arguments is rejected at {@link #render(String)} rather than
   * silently substituted.
   */
  public static CommandArgumentBinder forExecutor(String executor) {
    ExecutorShell resolved = ExecutorShell.from(executor);
    return new CommandArgumentBinder(resolved, !resolved.supportsBinding(), executor);
  }

  /**
   * Binder for a value that never reaches a shell (read-only display, DNS hostname): placeholders
   * are substituted verbatim, only control characters are stripped.
   */
  public static CommandArgumentBinder literal() {
    return new CommandArgumentBinder(ExecutorShell.NONE, false, null);
  }

  /**
   * Registers an argument value. Binding the same key twice keeps the first value (a key can appear
   * several times in a template but resolves to a single value).
   *
   * @param argumentKey the raw key as written in {@code #{key}}
   * @param value the resolved value, possibly attacker-controlled
   */
  public void bind(String argumentKey, String value) {
    if (variablesByKey.containsKey(argumentKey)) {
      return;
    }
    String sanitized = sanitize(value == null ? "" : value);
    if (!shell.canRepresent(sanitized)) {
      // Fail closed rather than render a declaration the shell would read differently than the
      // template describes. Logged so a refusal is visible in operation, not only to the caller.
      log.error(
          "Refusing to bind argument '{}' for executor '{}': the value cannot be represented in "
              + "this shell's declaration syntax.",
          argumentKey,
          executor);
      throw new CommandBindingException(
          "Argument '%s' cannot be represented for executor '%s': a double quote is not supported in a %s command value."
              .formatted(argumentKey, executor, shell.name().toLowerCase(Locale.ROOT)));
    }
    if (!shell.supportsBinding()) {
      // Literal mode: keep the value, no variable is declared.
      variablesByKey.put(argumentKey, null);
      valuesByVariable.put(argumentKey, sanitized);
      return;
    }
    String variable = allocateVariableName(argumentKey);
    variablesByKey.put(argumentKey, variable);
    valuesByVariable.put(variable, sanitized);
  }

  /**
   * Replaces every bound placeholder by its variable reference and prepends the declarations.
   *
   * @param template the command template authored on the payload
   * @return the command ready to be obfuscated and encoded
   */
  public String render(String template) {
    if (template == null) {
      return null;
    }
    // Fail closed: an unmapped executor must never fall back to verbatim substitution, which is
    // exactly the command injection this class exists to prevent. Templates without any argument
    // are still rendered, so payloads using an exotic executor keep working.
    if (rejectPlaceholders && !variablesByKey.isEmpty()) {
      log.error(
          "Refusing to render a command with argument placeholders for executor '{}': no binding "
              + "strategy available. Add the executor to ExecutorShell to enable safe binding.",
          executor);
      throw new CommandBindingException(
          "Unsupported executor '%s' for argument binding: refusing to render a command with placeholders."
              .formatted(executor));
    }
    String rendered = substitutePlaceholders(template);
    if (!shell.supportsBinding() || valuesByVariable.isEmpty()) {
      return rendered;
    }
    return buildPrologue() + rendered;
  }

  // -- PLACEHOLDER SUBSTITUTION --

  /**
   * Replaces every bound {@code #{key}} in a single pass over the template, so a substituted value
   * is never re-examined. Reading the template more than once would let the value of one argument
   * be interpreted as a placeholder naming another, which would make the result depend on the order
   * the arguments happened to be bound.
   *
   * <p>In binding mode a pair of quotes directly wrapping a placeholder is dropped, since the
   * binder owns the quoting. In literal mode the template is left structurally untouched: it backs
   * the read-only display path, where {@code echo "#{host}"} must render as {@code echo
   * "localhost"}.
   */
  private String substitutePlaceholders(String template) {
    if (variablesByKey.isEmpty()) {
      return template;
    }
    Matcher matcher = placeholderPattern().matcher(template);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(result, Matcher.quoteReplacement(substitutionFor(matcher)));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /** Matches any bound placeholder, and in binding mode the quotes directly wrapping it. */
  private Pattern placeholderPattern() {
    String keys =
        variablesByKey.keySet().stream().map(Pattern::quote).collect(Collectors.joining("|"));
    return shell.supportsBinding()
        ? Pattern.compile("(['\"])?#\\{(" + keys + ")}\\1?")
        : Pattern.compile("()#\\{(" + keys + ")}");
  }

  private String substitutionFor(Matcher matcher) {
    String argumentKey = matcher.group(2);
    if (!shell.supportsBinding()) {
      return valuesByVariable.get(argumentKey);
    }
    String replacement = reference(variablesByKey.get(argumentKey));
    // The quote group is optional, so it is absent rather than empty when the placeholder is bare.
    String openingQuote = matcher.group(1);
    if (openingQuote == null || openingQuote.isEmpty()) {
      return replacement;
    }
    // Only drop the quotes when they actually form a pair around the placeholder.
    return matcher.group().endsWith(openingQuote) ? replacement : openingQuote + replacement;
  }

  // -- DECLARATIONS --

  private String buildPrologue() {
    StringBuilder prologue = new StringBuilder();
    valuesByVariable.forEach(
        (variable, value) -> prologue.append(declaration(variable, value)).append(separator()));
    return prologue.toString();
  }

  private String declaration(String variable, String value) {
    return switch (shell) {
      case SH -> variable + "=" + quoteSh(value);
      case POWERSHELL -> "$" + variable + " = " + quotePowerShell(value);
      case CMD -> "set \"" + variable + "=" + escapeCmd(value) + "\"";
      case NONE -> "";
    };
  }

  private String reference(String variable) {
    return switch (shell) {
      // Double quotes prevent word splitting and globbing; adjacent-string concatenation makes it
      // work even when the placeholder sits inside a wider double-quoted literal.
      case SH -> "\"$" + variable + "\"";
      // PowerShell never word-splits a variable, so a bare reference is both safe and composable.
      case POWERSHELL -> "${" + variable + "}";
      // Delayed expansion: the content of !VAR! is not re-parsed for metacharacters.
      case CMD -> "\"!" + variable + "!\"";
      case NONE -> "";
    };
  }

  /** Statement separator: cmd is collapsed to a single line downstream, others stay multiline. */
  private String separator() {
    return shell == ExecutorShell.CMD ? " & " : "\n";
  }

  // -- ESCAPING --

  /** Single quotes make the value fully literal in POSIX shells; {@code '} is the only escape. */
  private static String quoteSh(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }

  /** Single-quoted PowerShell strings do not interpolate; {@code '} is doubled to escape it. */
  private static String quotePowerShell(String value) {
    return "'" + value.replace("'", "''") + "'";
  }

  /**
   * Escapes a value for {@code set "VAR=value"}. Inside the quoted form {@code & | < > ^ ( )} are
   * already literal; only percent expansion and delayed expansion need neutralising. A value that
   * cannot be carried by this form at all is refused earlier, see {@link
   * ExecutorShell#canRepresent(String)}. Line separators are already gone by then, removed for
   * every engine by {@link #sanitize(String)}.
   */
  private static String escapeCmd(String value) {
    return value.replace("%", "%%").replace("!", "^^!");
  }

  /** Drops the characters listed on {@link #NUL_AND_CONTROL_CHARS}. */
  private static String sanitize(String value) {
    return NUL_AND_CONTROL_CHARS.matcher(value).replaceAll("");
  }

  // -- VARIABLE NAMING --

  private String allocateVariableName(String argumentKey) {
    String base =
        VARIABLE_PREFIX
            + NON_IDENTIFIER_CHARS.matcher(argumentKey).replaceAll("_").toUpperCase(Locale.ROOT);
    String candidate = base;
    int suffix = 1;
    // Two distinct keys can sanitize to the same identifier (e.g. "my-arg" and "my.arg").
    while (valuesByVariable.containsKey(candidate)) {
      candidate = base + "_" + suffix++;
    }
    return candidate;
  }
}
