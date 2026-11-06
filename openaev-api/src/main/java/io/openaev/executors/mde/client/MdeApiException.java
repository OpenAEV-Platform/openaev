package io.openaev.executors.mde.client;

import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_NAME;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.executors.exception.ExecutorException;
import lombok.Getter;

/** A non-2xx answer of the MDE API, with the MDE error code when the body carries one. */
@Getter
public class MdeApiException extends ExecutorException {

  private static final String ACTIVE_REQUEST_ALREADY_EXISTS = "ActiveRequestAlreadyExists";
  private static final int TOO_MANY_REQUESTS = 429;

  private final int status;
  private final String errorCode;

  public MdeApiException(String uri, int status, String body, ObjectMapper objectMapper) {
    this(uri, status, readError(body, objectMapper));
  }

  private MdeApiException(String uri, int status, JsonNode error) {
    super(
        "MDE API " + uri + " failed: HTTP " + status + " " + error.path("message").asText(""),
        MDE_EXECUTOR_NAME);
    this.status = status;
    this.errorCode = error.path("code").asText(null);
  }

  /** MDE allows one Live Response session per device: another action is still running on it. */
  public boolean isDeviceBusy() {
    return ACTIVE_REQUEST_ALREADY_EXISTS.equals(errorCode);
  }

  public boolean isRateLimited() {
    return status == TOO_MANY_REQUESTS;
  }

  private static JsonNode readError(String body, ObjectMapper objectMapper) {
    try {
      return objectMapper.readTree(body).path("error");
    } catch (Exception e) {
      return objectMapper.missingNode();
    }
  }
}
