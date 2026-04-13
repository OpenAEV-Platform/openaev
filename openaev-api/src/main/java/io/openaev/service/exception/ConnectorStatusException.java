package io.openaev.service.exception;

import io.openaev.rest.exception.UnprocessableContentException;

public class ConnectorStatusException extends UnprocessableContentException {
  public ConnectorStatusException(String message) {
    super(message);
  }
}
