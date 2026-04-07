package io.openaev.api.exception;

public class ElementNotFoundException extends RuntimeException {

  public ElementNotFoundException() {
    super();
  }

  public ElementNotFoundException(String errorMessage) {
    super(errorMessage);
  }
}
