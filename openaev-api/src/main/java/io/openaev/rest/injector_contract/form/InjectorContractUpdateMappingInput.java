package io.openaev.rest.injector_contract.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InjectorContractUpdateMappingInput {
  @JsonProperty("contract_attack_patterns_ids")
  private List<String> attackPatternsIds = new ArrayList<>();

  @JsonProperty("contract_vulnerability_ids")
  private List<String> vulnerabilityIds = new ArrayList<>();
}
