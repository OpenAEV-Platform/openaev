package io.openaev.exception;

import lombok.Getter;

@Getter
public class AnalyticsEngineException extends RuntimeException {

  public AnalyticsEngineException(String message) {
    super(message);
  }

  public AnalyticsEngineException(String message, Exception e) {
    super(message, e);
  }
}
