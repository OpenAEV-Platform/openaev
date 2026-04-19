package io.openaev.integration.exception;

import io.openaev.rest.exception.ElementNotFoundException;

public class ComponentNotFoundException extends ElementNotFoundException {
  public ComponentNotFoundException(String message) {
    super(message);
  }
}
