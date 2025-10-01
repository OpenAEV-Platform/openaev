package io.openaev.rest.mitigation.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MitigationUpsertInput {

  @JsonProperty("mitigations")
  private List<MitigationCreateInput> mitigations = new ArrayList<>();
}
