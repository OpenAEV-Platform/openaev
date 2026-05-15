package io.openaev.database.model;

import lombok.Data;

@Data
public abstract class BaseConnectorEntity implements Base {
  private String id;
  private String name;
  private String type;
  private boolean external;
}
