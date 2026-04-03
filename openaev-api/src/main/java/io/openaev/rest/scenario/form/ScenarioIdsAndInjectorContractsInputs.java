package io.openaev.rest.scenario.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ScenarioIdsAndInjectorContractsInputs {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("locale")
  private String locale;

  @NotEmpty(message = MANDATORY_MESSAGE)
  @JsonProperty("scenario_ids")
  private List<String> scenarioIds;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("injector_contract_search_pagination_input")
  private InjectorContractSearchPaginationInput injectorContractSearchPaginationInput;
}
