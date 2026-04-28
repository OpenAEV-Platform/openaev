package io.openaev.rest.scenario.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScenarioAndInjectorContractsInputs {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("locale")
  private String locale;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("scenario_input")
  private ScenarioInput scenarioInput;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("injector_contract_search_pagination_input")
  private InjectorContractSearchPaginationInput injectorContractSearchPaginationInput;
}
