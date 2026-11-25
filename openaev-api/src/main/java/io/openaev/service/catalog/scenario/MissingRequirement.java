package io.openaev.service.catalog.scenario;

/**
 * What a catalogue scenario lacks to install, the name to show for it (its integration's title when
 * the catalogue has one), the integration that brings it and where that integration stands in the
 * tenant, when known.
 */
public record MissingRequirement(String label, String name, String integrationId, Status status) {

  public enum Status {
    NOT_DEPLOYED,
    NOT_RUNNING;

    static Status of(boolean deployed) {
      return deployed ? NOT_RUNNING : NOT_DEPLOYED;
    }
  }
}
