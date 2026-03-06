package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Semantic binding annotation for an input argument/field.
 *
 * <p>Links a payload argument or contract field to a specific sub-field of an upstream output type.
 * For example, {@code input_type = "credentials", input_field = "username"} means this input
 * expects the {@code username} sub-field from a {@code credentials} output.
 *
 * <p>For scalar output types (text, number, ipv4, ipv6, port), {@code input_field} is {@code
 * null} because there are no sub-fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSource {

  @JsonProperty("input_type")
  @Schema(description = "The ContractOutputType value this input consumes (e.g. credentials, portscan, ipv4)")
  private String inputType;

  @JsonProperty("input_field")
  @Schema(
      description =
          "The sub-field key within the input type (e.g. username, host). Null for scalar types.",
      types = {"string", "null"})
  private String inputField;
}
