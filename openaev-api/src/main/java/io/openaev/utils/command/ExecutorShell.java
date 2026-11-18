package io.openaev.utils.command;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Shell families supported for structured argument binding.
 *
 * <p>Each family knows how to declare a variable and how to reference it inside a command template,
 * so that an argument value can never alter the structure of the command being executed.
 */
public enum ExecutorShell {

  /** {@code sh}, {@code bash}, {@code zsh}, ... — POSIX-like shells. */
  SH(
      Pattern.compile(
          "\\$\\{(?<braced>[A-Za-z_][A-Za-z0-9_]*)}|\\$(?<name>[A-Za-z_][A-Za-z0-9_]*)"),
      "") {
    @Override
    String environmentVariableReference(MatchResult variable) {
      return "\"${"
          + Objects.requireNonNullElse(variable.group("braced"), variable.group("name"))
          + "}\"";
    }
  },

  /** {@code psh}, {@code pwsh}, {@code powershell}. */
  POWERSHELL(
      Pattern.compile("\\$env:(?<name>[A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE), " + ") {
    @Override
    String environmentVariableReference(MatchResult variable) {
      return "$env:" + variable.group("name");
    }
  },

  /** Windows {@code cmd.exe} (requires delayed expansion, already assumed by the implant). */
  CMD(Pattern.compile("%(?<name>[A-Za-z_][A-Za-z0-9_]*)%"), "") {
    @Override
    String environmentVariableReference(MatchResult variable) {
      return "!" + variable.group("name") + "!";
    }
  },

  /**
   * No shell involved (e.g. a DNS hostname template): values are substituted literally, only
   * control characters are stripped. Never use this for anything that ends up on a command line.
   */
  NONE(null, "") {
    @Override
    String environmentVariableReference(MatchResult variable) {
      throw new UnsupportedOperationException("No shell: nothing is declared");
    }
  };

  /**
   * An environment variable reference as a payload author writes it in a value. Only a plain name
   * is accepted, so it cannot change the structure of the command.
   */
  final Pattern environmentVariable;

  /** Joins the literal parts and the references of one declared value. */
  final String concatenation;

  ExecutorShell(Pattern environmentVariable, String concatenation) {
    this.environmentVariable = environmentVariable;
    this.concatenation = concatenation;
  }

  /** The reference that expands the matched variable inside a declared value. */
  abstract String environmentVariableReference(MatchResult variable);

  public static ExecutorShell from(String executor) {
    if (executor == null || executor.isBlank()) {
      return NONE;
    }
    return switch (executor.trim().toLowerCase(Locale.ROOT)) {
      case "sh", "bash", "zsh", "ash", "dash" -> SH;
      case "psh", "pwsh", "powershell", "powershell.exe" -> POWERSHELL;
      case "cmd", "cmd.exe" -> CMD;
      default -> NONE;
    };
  }

  public boolean supportsBinding() {
    return this != NONE;
  }

  /**
   * Whether this shell's declaration syntax can carry the given value in full.
   *
   * <p>{@link #CMD} declares with {@code set "VAR=value"}. That quoted region ends at the next
   * {@code "} and offers no escape for one inside it: {@code ^} is literal there. A value carrying
   * a double quote therefore cannot be declared, and is refused rather than rendered into something
   * other than what the template describes.
   *
   * <p>The other families quote with {@code '}, which they do know how to escape, so every value is
   * representable for them.
   */
  public boolean canRepresent(String value) {
    return this != CMD || !value.contains("\"");
  }

  /**
   * Why {@link #canRepresent(String)} turned a value away, phrased for the caller who supplied it.
   * Kept next to the rule so the two cannot drift apart.
   */
  public String unrepresentableValueReason() {
    return this == CMD
        ? "a double quote is not supported in a cmd command value"
        : "the value cannot be represented in this shell";
  }
}
