package io.openaev.utils.command;

/**
 * Raised when a command template cannot be rendered safely.
 *
 * <p>Declared in this package on purpose: {@link CommandArgumentBinder} is a low-level utility and
 * must not depend on the REST exception hierarchy (layer inversion). Callers in the API layer are
 * free to translate it into whatever HTTP semantics they need.
 */
public class CommandBindingException extends RuntimeException {

  public CommandBindingException(String message) {
    super(message);
  }
}
