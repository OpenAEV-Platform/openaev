package io.openaev.rest.exception;

/**
 * A built-in connector row is missing for the tenant serving the request: its registration never
 * ran or failed. Transient, so callers are told to retry (503) rather than to fix their request.
 */
public class TenantConnectorNotReadyException extends RuntimeException {

  public TenantConnectorNotReadyException(String message) {
    super(message);
  }
}
