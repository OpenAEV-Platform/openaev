package io.openaev.rest.payload.output_parser;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ParserMode;
import io.openaev.database.model.ParserType;
import io.openaev.rest.payload.contract_output_element.ContractOutputElementSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Represents a single output parser")
public class OutputParserSimple {

  @JsonProperty("output_parser_id")
  @NotBlank
  private String id;

  @JsonProperty("output_parser_mode")
  @Schema(
      description = "Mode of parser, which output will be parsed, for now only STDOUT is supported")
  @NotNull
  private ParserMode mode;

  @JsonProperty("output_parser_type")
  @Schema(description = "Type of parser, for now only REGEX is supported")
  @NotNull
  private ParserType type;

  @JsonProperty("output_parser_contract_output_elements")
  @NotNull
  private Set<ContractOutputElementSimple> contractOutputElement;
}
