package io.openaev.rest.payload.contract_output_element;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.payload.regex_group.RegexGroupSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Represents the rules for parsing the output of an execution.")
public class ContractOutputElementSimple {

  @JsonProperty("contract_output_element_id")
  @NotBlank
  private String id;

  @JsonProperty("contract_output_element_rule")
  @Schema(description = "The rule to apply for parsing the output, for example, can be a regex.")
  @NotBlank
  private String rule;

  @JsonProperty("contract_output_element_name")
  @Schema(description = "Represents the name of the rule.")
  @NotBlank
  private String name;

  @JsonProperty("contract_output_element_key")
  @Schema(description = "Represents a unique key identifier.")
  @NotBlank
  private String key;

  @JsonProperty("contract_output_element_type")
  @Schema(
      description = "Represents the data type being extracted.",
      example = "text, number, port, portscan, ipv4, ipv6, credentials")
  @NotNull
  private ContractOutputType type;

  @JsonProperty("contract_output_element_tags")
  private List<String> tagIds;

  @JsonProperty("contract_output_element_regex_groups")
  @NotNull
  private Set<RegexGroupSimple> regexGroups;
}
