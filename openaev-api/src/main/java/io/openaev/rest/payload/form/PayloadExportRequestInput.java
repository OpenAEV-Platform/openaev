package io.openaev.rest.payload.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class PayloadExportRequestInput {
  @JsonProperty("payloads")
  private List<PayloadExportTarget> payloads;

  @JsonIgnore
  public List<String> getTargetsIds() {
    if (payloads == null) {
      return List.of();
    }
    return payloads.stream().map(PayloadExportTarget::getId).toList();
  }
}
