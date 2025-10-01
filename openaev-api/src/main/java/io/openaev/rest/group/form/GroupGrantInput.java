package io.openaev.rest.group.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Grant;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GroupGrantInput {

  @JsonProperty("grant_name")
  private Grant.GRANT_TYPE name;

  @JsonProperty("grant_resource")
  private String resourceId;

  @JsonProperty("grant_resource_type")
  private Grant.GRANT_RESOURCE_TYPE resourceType;
}
