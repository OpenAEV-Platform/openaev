package io.openaev.xtmhub;

public enum XtmHubConnectivityStatus {
  ACTIVE("active"),
  INACTIVE("inactive"),
  NOT_FOUND("not_found");

  public final String label;

  XtmHubConnectivityStatus(String label) {
    this.label = label;
  }
}
