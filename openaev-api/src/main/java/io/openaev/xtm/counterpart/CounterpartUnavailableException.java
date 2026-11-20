package io.openaev.xtm.counterpart;

/** A connected product could not answer a counterpart lookup. Never surfaced as a page error. */
public class CounterpartUnavailableException extends Exception {

  public CounterpartUnavailableException(String message) {
    super(message);
  }

  public CounterpartUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
