package io.openaev.executors.exception;

/** The EDR refuses the call for now (device busy, rate limit): the dispatch retries it later. */
public class ExecutorBusyException extends ExecutorException {

  public ExecutorBusyException(String message, String executorName) {
    super(message, executorName);
  }
}
