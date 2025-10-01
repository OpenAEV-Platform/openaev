package io.openaev.rest.payload.contract_output_element;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.payload.regex_group.RegexGroupInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;

@Data
public class ContractOutputElementInput {

  @JsonProperty("contract_output_element_id")
  private String id;

  @JsonProperty("contract_output_element_is_finding")
  @Schema(
      description =
          "Indicates whether this contract output element can be used to generate a finding")
  @NotNull
  private boolean isFinding;

  @JsonProperty("contract_output_element_rule")
  @Schema(description = "Parser Rule")
  @NotBlank
  private String rule;

  @JsonProperty("contract_output_element_name")
  @Schema(description = "Name")
  @NotBlank
  private String name;

  @JsonProperty("contract_output_element_key")
  @Schema(description = "Key")
  @NotBlank
  private String key;

  @JsonProperty("contract_output_element_type")
  @Schema(
      description =
          "Contract Output element type, can be: text, number, port, IPV6, IPV4, portscan, credentials")
  @NotNull
  private ContractOutputType type;

  @JsonProperty("contract_output_element_tags")
  @Schema(description = "List of tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("contract_output_element_regex_groups")
  @Schema(description = "Set of regex groups")
  @NotNull
  private Set<RegexGroupInput> regexGroups = new HashSet<>();
}
