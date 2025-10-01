package io.openaev.stix.parsing;

public class ParsingException extends Exception {
  public ParsingException() {
    super();
  }

  public ParsingException(String message) {
    super(message);
  }

  public ParsingException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
