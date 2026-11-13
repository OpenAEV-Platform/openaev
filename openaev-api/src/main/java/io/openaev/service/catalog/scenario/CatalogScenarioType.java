package io.openaev.service.catalog.scenario;

/** How a catalogue scenario runs: its injects on a timeline, or the steps of a workflow. */
public enum CatalogScenarioType {
  TIME_BASED(true),
  CHAINED(false);

  private static final String CHAINED_VALUE = "chained";

  private final boolean installedInBulk;

  CatalogScenarioType(boolean installedInBulk) {
    this.installedInBulk = installedInBulk;
  }

  /** The type a manifest names; one that names none, as older ones do, is time-based. */
  public static CatalogScenarioType of(String manifestType) {
    return CHAINED_VALUE.equalsIgnoreCase(manifestType) ? CHAINED : TIME_BASED;
  }

  /** Whether an install-all or an auto-install takes it; a chained one waits for its scope. */
  public boolean installedInBulk() {
    return installedInBulk;
  }
}
