package io.openaev.exception;

/** A new provider or endpoint for an AI target needs its token again, or its removal. */
public class AiTargetTokenRequiredException extends ActionRefusedException {

  public AiTargetTokenRequiredException() {
    super("The provider or the endpoint changed: enter the token again, or remove it.");
  }
}
