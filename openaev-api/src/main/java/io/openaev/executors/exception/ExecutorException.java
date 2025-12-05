package io.openaev.executors.exception;

import lombok.Getter;

@Getter
public class ExecutorException extends RuntimeException {

  private static final String EXECUTOR_EXCEPTION = " executor exception - ";

  public ExecutorException(String message, String executorName) {
    super(executorName + EXECUTOR_EXCEPTION + message);
  }

  public ExecutorException(Throwable cause, String message, String executorName) {
    super(executorName + EXECUTOR_EXCEPTION + message, cause);
  }
}
