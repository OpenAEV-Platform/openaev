package io.openaev.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents the execution mode for an injection process.
 *
 * <p>This class determines how an injection should be executed:
 *
 * <ul>
 *   <li><b>Asynchronous</b> ({@code async=true}) - The injection is queued and executed in the
 *       background. Control returns immediately to the caller.
 *   <li><b>Synchronous</b> ({@code async=false}) - The injection is executed immediately and the
 *       caller waits for completion.
 * </ul>
 *
 * <p>Asynchronous execution is typically preferred for long-running injections or when executing
 * multiple injections in parallel.
 */
@Getter
@Setter
public class ExecutionProcess {

  /** Whether this process should execute asynchronously. */
  private boolean async;

  /**
   * Creates a new execution process with the specified mode.
   *
   * @param async true for asynchronous execution, false for synchronous
   */
  public ExecutionProcess(boolean async) {
    this.async = async;
  }

  /**
   * Creates an asynchronous execution process.
   *
   * @return an ExecutionProcess configured for async execution
   */
  public static ExecutionProcess async() {
    return new ExecutionProcess(true);
  }

  /**
   * Creates a synchronous execution process.
   *
   * @return an ExecutionProcess configured for sync execution
   */
  public static ExecutionProcess sync() {
    return new ExecutionProcess(false);
  }
}
