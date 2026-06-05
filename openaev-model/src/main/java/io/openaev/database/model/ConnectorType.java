package io.openaev.database.model;

import lombok.Getter;

@Getter
public enum ConnectorType {
  COLLECTOR("COLLECTOR_ID"),
  INJECTOR("INJECTOR_ID"),
  EXECUTOR("EXECUTOR_ID"),
  SECRETS_PROVIDER("SECRETS_PROVIDER_ID");

  public final String idKeyName;

  ConnectorType(String idKeyName) {
    this.idKeyName = idKeyName;
  }
}
