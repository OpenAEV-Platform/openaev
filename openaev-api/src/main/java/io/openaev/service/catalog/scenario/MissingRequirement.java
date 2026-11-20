package io.openaev.service.catalog.scenario;

/**
 * What a catalogue scenario lacks to install, with the integration that brings it and where that
 * integration stands in the tenant, when known.
 */
public record MissingRequirement(String label, String integrationId, Status status) {

  public enum Status {
    NOT_DEPLOYED,
    NOT_RUNNING;

    static Status of(boolean deployed) {
      return deployed ? NOT_RUNNING : NOT_DEPLOYED;
    }
  }
}
