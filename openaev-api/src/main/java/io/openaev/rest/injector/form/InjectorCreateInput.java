package io.openaev.rest.injector.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.injector_contract.form.InjectorContractInput;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public class InjectorCreateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("injector_id")
  private String id;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("injector_name")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("injector_type")
  private String type;

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

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<InjectorContractInput> getContracts() {
    return contracts;
  }

  public void setContracts(List<InjectorContractInput> contracts) {
    this.contracts = contracts;
  }

  public Boolean getCustomContracts() {
    return customContracts;
  }

  public void setCustomContracts(Boolean customContracts) {
    this.customContracts = customContracts;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public Map<String, String> getExecutorCommands() {
    return executorCommands;
  }

  public void setExecutorCommands(Map<String, String> executorCommands) {
    this.executorCommands = executorCommands;
  }

  public Map<String, String> getExecutorClearCommands() {
    return executorClearCommands;
  }

  public void setExecutorClearCommands(Map<String, String> executorClearCommands) {
    this.executorClearCommands = executorClearCommands;
  }

  public Boolean getPayloads() {
    return payloads;
  }

  public void setPayloads(Boolean payloads) {
    this.payloads = payloads;
  }
}
