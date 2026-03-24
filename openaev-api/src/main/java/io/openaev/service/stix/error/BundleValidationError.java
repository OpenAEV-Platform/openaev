package io.openaev.service.stix.error;

public class BundleValidationError extends Exception {
  public BundleValidationError(String message) {
    super(message);
  }
}
