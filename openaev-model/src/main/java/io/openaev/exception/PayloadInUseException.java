package io.openaev.exception;

import java.util.List;
import org.springframework.data.domain.Limit;

/** Deleting this payload would also delete the injects that run it. */
public class PayloadInUseException extends ActionRefusedException {

  private static final int MAX_LISTED = 3;

  /** The usages a refusal reads: those it lists, and one more to say there are others. */
  public static final Limit READ = Limit.of(MAX_LISTED + 1);

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
