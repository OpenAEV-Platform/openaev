package io.openaev.rest.mapper.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class InjectImporterAddInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("inject_importer_type_value")
  private String injectTypeValue;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("inject_importer_injector_contract")
  private String injectorContractId;

  @JsonProperty("inject_importer_rule_attributes")
  private List<RuleAttributeAddInput> ruleAttributes = new ArrayList<>();
}
