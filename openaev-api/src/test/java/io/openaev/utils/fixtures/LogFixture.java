package io.openaev.utils.fixtures;

import io.openaev.rest.log.form.LogDetailsInput;

public class LogFixture {

  public static LogDetailsInput getDefaultLogDetailsInput(String level) {
    LogDetailsInput logDetailsInput = new LogDetailsInput();
    logDetailsInput.setMessage(
        "Message error received: [@formatjs/intl] An `id` must be provided to format a message.");
    logDetailsInput.setStack("tt");
    logDetailsInput.setLevel(level);

    return logDetailsInput;
  }
}
