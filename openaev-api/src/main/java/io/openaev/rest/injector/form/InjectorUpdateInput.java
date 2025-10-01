package io.openaev.rest.injector.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.injector_contract.form.InjectorContractInput;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InjectorUpdateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("injector_name")
  private String name;

  @JsonProperty("injector_contracts")
  private List<InjectorContractInput> contracts;

  @JsonProperty("injector_custom_contracts")
  private Boolean customContracts = false;

  @JsonProperty("injector_category")
  private String category;

  @JsonProperty("injector_executor_commands")
  private Map<String, String> executorCommands;

  @JsonProperty("injector_executor_clear_commands")
  private Map<String, String> executorClearCommands;

  @JsonProperty("injector_payloads")
  private Boolean payloads = false;
}
