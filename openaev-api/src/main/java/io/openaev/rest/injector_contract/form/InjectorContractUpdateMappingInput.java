package io.openaev.rest.injector_contract.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("contract_domains")
  @Schema(description = "Set list of domains")
  private List<String> domainIds = new ArrayList<>();
}
