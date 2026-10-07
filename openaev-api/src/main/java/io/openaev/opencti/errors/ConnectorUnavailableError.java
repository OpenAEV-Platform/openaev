package io.openaev.opencti.errors;

import java.io.IOException;

/**
 * The tenant's OpenCTI connection cannot be used right now: no active connector, a connector not
 * registered yet (OpenCTI unreachable at the last register or ping), or OpenCTI rate limiting the
 * call. An {@link IOException} so callers that retry later treat it like an unreachable host, never
 * like OpenCTI refusing the request.
 */
public class ConnectorUnavailableError extends IOException {
  public ConnectorUnavailableError(String message) {
    super(message);
  }
}
