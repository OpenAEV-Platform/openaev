package io.openaev.api.detection_remediation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.payload.form.PayloadUpdateInput;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PayloadInput extends PayloadUpdateInput {

  @JsonProperty("payload_type")
  private String type;
}
