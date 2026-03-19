package io.openaev.sso;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GroupMapping {

  @JsonProperty("idpGroup")
  private String idpGroup;

  @JsonProperty("userGroup")
  private String userGroup;

  @JsonProperty("autoCreate")
  private boolean autoCreate;
}
