package io.openaev.execution;

/**
 * The inject cannot be executed, and running it again would fail the same way (no injector, no
 * started connector, empty inject, no agent able to run it...).
 *
 * <p>Callers record it on the inject as an ERROR instead of rolling back and retrying: a retry only
 * loses the inject and its traces. Technical failures must not use it, so they keep the rollback
 * and retry.
 */
public class UnexecutableInjectException extends RuntimeException {
  public UnexecutableInjectException(String message) {
    super(message);
  }
}
