package io.openaev.database.model;

/** Logical groups used to organize capabilities in the UI tree. */
public enum CapabilityGroup {
  SUPERUSER(0),
  DASHBOARDS(1),
  REPORTINGS(2),
  FINDINGS(3),
  ASSESSMENT(4),
  THREAT_ARSENALS(5),
  CREDENTIALS(6),
  TARGETS(7),
  CONTENT(8),
  PLATFORM_SETTINGS(9),
  TENANT_SETTINGS(10),
  SECURITY(11),
  TAXONOMY(12),
  TENANTS(13),
  STIX(14),
  SERVICE(15);

  private final int uiOrder;

  CapabilityGroup(int uiOrder) {
    this.uiOrder = uiOrder;
  }

  public int getUiOrder() {
    return uiOrder;
  }
}
