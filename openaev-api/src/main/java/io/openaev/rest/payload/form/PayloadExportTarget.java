package io.openaev.rest.payload.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PayloadExportTarget {
  @JsonProperty("payload_id")
  private String id;
}
