package io.openbas.xtmhub;

public enum XtmHubRegistrationStatus {
  REGISTERED("registered"),
  UNREGISTERED("unregistered"),
  LOST_CONNECTIVITY("lost_connectivity");

  public final String label;

  XtmHubRegistrationStatus(String label) {
    this.label = label;
  }
}
