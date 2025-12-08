package io.openaev.rest.injector_contract.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InjectorContractInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("contract_id")
  private String id;

  @JsonProperty("contract_manual")
  private boolean manual = false;

  @JsonProperty("contract_labels")
  private Map<String, String> labels;

  @JsonProperty("contract_attack_patterns_external_ids")
  private List<String> attackPatternsExternalIds = new ArrayList<>();

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("contract_content")
  private String content;

  @JsonProperty("is_atomic_testing")
  private boolean isAtomicTesting = true;

  @JsonProperty("contract_platforms")
  private PLATFORM_TYPE[] platforms = new PLATFORM_TYPE[0];

  @JsonProperty("contract_domains")
  private Set<Domain> domains = new HashSet<>();
}
