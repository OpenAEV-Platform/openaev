package io.openaev.rest.exception;

import java.util.List;

/** Deleting this payload would cascade to the injects that run it. */
public class PayloadInUseException extends BadRequestException {

  private static final int MAX_LISTED = 10;

  public PayloadInUseException(List<String> usages) {
    super(message(usages));
  }

  private static String message(List<String> usages) {
    String listed = String.join(", ", usages.stream().limit(MAX_LISTED).toList());
    int more = usages.size() - MAX_LISTED;
    return "This payload is used by injects in: "
        + listed
        + (more > 0 ? " and " + more + " more" : "")
        + ". Move them to another payload or remove them first.";
  }
}
