package io.openaev.utils;

import io.openaev.rest.log.form.LogDetailsInput;

public class LogUtils {

  public static String buildLogMessage(LogDetailsInput logDetailsInput, String level) {
    return "Message "
        + level
        + " received: "
        + logDetailsInput.getMessage()
        + " stacktrace: "
        + logDetailsInput.getStack();
  }
}
