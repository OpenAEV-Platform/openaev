package io.openaev.rest.exception;

public class ResourceInUseException extends Exception {

  public ResourceInUseException(String errorMessage) {
    super(errorMessage);
  }

  public ResourceInUseException(String errorMessage, Exception cause) {
    super(errorMessage, cause);
  }
}
