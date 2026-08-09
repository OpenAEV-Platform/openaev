package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.InjectorContract;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
    description =
        "Injector contract referenced by a workflow step, exposed for the logic screen. "
            + "Only the fields needed to render the action form are returned.")
public record WorkflowInjectorContractOutput(
    @Schema(description = "Injector contract Id") @JsonProperty("injector_contract_id") @NotBlank
        String id,
    @Schema(description = "Injector contract content (serialized fields)")
        @JsonProperty("injector_contract_content")
        String content) {

  public static WorkflowInjectorContractOutput fromInjectorContract(InjectorContract contract) {
    return new WorkflowInjectorContractOutput(contract.getId(), contract.getContent());
  }
}
