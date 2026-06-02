package io.openaev.database.id;

import java.io.Serializable;

public class CompositeId implements Serializable {
  private String tenant;
  private String id;

  public CompositeId() {}

  public CompositeId(String tenantId, String id) {
    this.tenant = tenantId;
    this.id = id;
  }
}
