package io.openaev.utils.command;

import java.util.Locale;

/**
 * Shell families supported for structured argument binding.
 *
 * <p>Each family knows how to declare a variable and how to reference it inside a command template,
 * so that an argument value can never alter the structure of the command being executed.
 */
public enum ExecutorShell {

  /** {@code sh}, {@code bash}, {@code zsh}, ... — POSIX-like shells. */
  SH,

  /** {@code psh}, {@code pwsh}, {@code powershell}. */
  POWERSHELL,

  /** Windows {@code cmd.exe} (requires delayed expansion, already assumed by the implant). */
  CMD,

  /**
   * No shell involved (e.g. a DNS hostname template): values are substituted literally, only
   * control characters are stripped. Never use this for anything that ends up on a command line.
   */
  NONE;

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
}
