package io.openaev.scheduler.jobs.exception;

import java.util.List;

public class ErrorMessagesPreExecutionException extends Exception {

  public ErrorMessagesPreExecutionException(List<String> messages) {
    super(formatMessages(messages));
  }

  private static String formatMessages(List<String> messages) {
    if (messages == null || messages.isEmpty()) {
      return "An unknown error occurred before execution.";
    }
    return String.join("; ", messages);
  }
}
