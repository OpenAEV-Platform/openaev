package io.openbas.xtmhub;

public enum XtmHubConnectivityStatus {
  ACTIVE("active"),
  INACTIVE("inactive");

  public final String label;

  XtmHubConnectivityStatus(String label) {
    this.label = label;
  }
}
