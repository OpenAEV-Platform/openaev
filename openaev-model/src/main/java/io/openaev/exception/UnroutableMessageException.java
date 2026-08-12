package io.openaev.exception;

/**
 * Thrown when the broker returned a published message because no queue was bound to its routing key
 * — typically a connector that never declared its queue. Without it the discard is silent, and the
 * inject is marked as published into the void.
 */
public class UnroutableMessageException extends RuntimeException {

  /**
   * Constructs a new unroutable message exception with the specified message.
   *
   * @param message the detail message describing which routing key could not be routed
   */
  public UnroutableMessageException(String message) {
    super(message);
  }
}
