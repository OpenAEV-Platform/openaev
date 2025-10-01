package io.openaev.exception;

import lombok.Getter;

@Getter
public class StartupException extends RuntimeException {

  public StartupException(String message) {
    super(message);
  }

  public StartupException(String message, Exception e) {
    super(message, e);
  }
}
