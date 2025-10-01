package io.openaev.injector_contract.outputs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import lombok.Data;

@Data
public class InjectorContractContentOutputElement {
  @JsonProperty("type")
  private ContractOutputType type;

  @JsonProperty("field")
  private String field;

  @JsonProperty("labels")
  private String[] labels;

  @JsonProperty("isMultiple")
  boolean isMultiple;

  @JsonProperty("isFindingCompatible")
  boolean isFindingCompatible;
}
