package io.openaev.database.model;

public enum ConnectorType {
  COLLECTOR("COLLECTOR_ID"),
  INJECTOR("INJECTOR_ID"),
  EXECUTOR("EXECUTOR_ID");

  public final String idKeyName;

  ConnectorType(String idKeyName) {
    this.idKeyName = idKeyName;
  }

  public String getIdKeyName() {
    return this.idKeyName;
  }
}
