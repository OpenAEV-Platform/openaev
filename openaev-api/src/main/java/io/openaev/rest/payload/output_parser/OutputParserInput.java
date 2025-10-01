package io.openaev.rest.payload.output_parser;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ParserMode;
import io.openaev.database.model.ParserType;
import io.openaev.rest.payload.contract_output_element.ContractOutputElementInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;

@Data
public class OutputParserInput {

  @JsonProperty("output_parser_id")
  private String id;

  @JsonProperty("output_parser_mode")
  @Schema(description = "Paser Mode: STDOUT, STDERR, READ_FILE")
  @NotNull
  private ParserMode mode;

  @JsonProperty("output_parser_type")
  @Schema(description = "Parser Type: REGEX")
  @NotNull
  private ParserType type;

  @JsonProperty("output_parser_contract_output_elements")
  @Schema(description = "List of Contract output elements")
  @NotNull
  private Set<ContractOutputElementInput> contractOutputElements = new HashSet<>();
}
