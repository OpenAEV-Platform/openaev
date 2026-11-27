package io.openaev.rest.exception;

/**
 * The caller tried to define, move or assign a marking above their own clearance.
 *
 * <p>Its {@code message} is the {@link #CODE} the client translates, not a sentence: the default
 * error body drops the message of a {@link ForbiddenException}, so {@code RestBehavior} sends the
 * code itself.
 */
public class MarkingAboveClearanceException extends ForbiddenException {

  public static final String CODE = "CANNOT_DEFINE_MARKING_ABOVE_CLEARANCE";

  public MarkingAboveClearanceException() {
    super(CODE);
  }
}
