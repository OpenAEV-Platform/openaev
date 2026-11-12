package io.openaev.exception;

/**
 * Thrown when a widget or search asks the analytics engine for a field that is not indexed.
 *
 * <p>The field comes from the caller, so the REST layer maps it to HTTP 400 instead of a 500.
 */
public class UnknownEngineFieldException extends AnalyticsEngineException {

  public UnknownEngineFieldException(String field) {
    super("Unknown field: " + field);
  }
}
