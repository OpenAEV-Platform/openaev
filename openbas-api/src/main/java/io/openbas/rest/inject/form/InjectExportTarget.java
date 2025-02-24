package io.openbas.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InjectExportTarget {
  @JsonProperty("inject_id")
  private String id;
}
