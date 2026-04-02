package io.openaev.rest.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

@Data
public class ScenarioAndInjectorContractsInputs {

    @NotNull(message = MANDATORY_MESSAGE)
    @JsonProperty("scenario_input")
    private ScenarioInput scenarioInput;

    @NotNull(message = MANDATORY_MESSAGE)
    @JsonProperty("injector_contract_search_pagination_input")
    private InjectorContractSearchPaginationInput injectorContractSearchPaginationInput;

}
