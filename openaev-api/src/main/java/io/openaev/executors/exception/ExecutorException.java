package io.openaev.executors.exception;

import lombok.Getter;

@Getter
public class ExecutorException extends RuntimeException {

  public ExecutorException(String message, String executorName) {
    super(executorName + " executor exception -" + message);
  }

  public ExecutorException(Throwable cause, String message, String executorName) {
    super(executorName + " executor exception -" + message, cause);
  }
}
