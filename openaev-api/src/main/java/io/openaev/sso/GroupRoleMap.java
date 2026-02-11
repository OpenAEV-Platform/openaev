package io.openaev.sso;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GroupRoleMap {

  @JsonProperty("IDPRole")
  private String IDPRole;
  @JsonProperty("OAEVGroup")
  private String OAEVGroup;
  @JsonProperty("autoCreate")
  private boolean autoCreate;
}
