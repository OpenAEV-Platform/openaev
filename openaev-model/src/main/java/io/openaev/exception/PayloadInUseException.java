package io.openaev.exception;

import java.util.List;

/** Deleting this payload would also delete the injects that run it. */
public class PayloadInUseException extends RuntimeException {

  public static final int MAX_LISTED = 3;

  public PayloadInUseException(List<String> usages) {
    super(message(usages));
  }

  private static String message(List<String> usages) {
    String listed = String.join(", ", usages.stream().limit(MAX_LISTED).toList());
    return "This payload is used by injects in: "
        + listed
        + (usages.size() > MAX_LISTED ? " and more" : "")
        + ". Move them to another payload or remove them first.";
  }
}
