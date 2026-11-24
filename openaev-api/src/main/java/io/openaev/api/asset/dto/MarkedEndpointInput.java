package io.openaev.api.asset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class MarkedEndpointInput extends EndpointInput {

  // Absent on an update: the markings stay as they are.
  @JsonProperty("asset_markings")
  private Set<String> markingIds;
}
