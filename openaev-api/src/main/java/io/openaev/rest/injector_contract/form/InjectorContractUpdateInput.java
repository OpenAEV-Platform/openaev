package io.openaev.rest.injector_contract.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InjectorContractUpdateInput {
  @JsonProperty("contract_manual")
  private boolean manual = false;

  @JsonProperty("contract_labels")
  private Map<String, String> labels;

  @JsonProperty("contract_attack_patterns_ids")
  private List<String> attackPatternsIds = new ArrayList<>();

  @JsonProperty("contract_vulnerability_ids")
  private List<String> vulnerabilityIds = new ArrayList<>();

  @JsonProperty("contract_vulnerability_external_ids")
  private List<String> vulnerabilityExternalIds = new ArrayList<>();

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("contract_content")
  private String content;

  @JsonProperty("is_atomic_testing")
  private boolean isAtomicTesting = true;

  @JsonProperty("contract_platforms")
  private String[] platforms = new String[0];

  @JsonProperty("contract_domains")
  private Set<InjectorContractDomainDTO> domains;
}
