package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.PrimitiveType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * Everything the frontend needs to build and validate a condition on a given {@link PrimitiveType},
 * without restating any backend knowledge of its own.
 *
 * <p>Two independent facets are exposed: {@code capabilities} answers "which operators and controls
 * should this field offer" and is read before the user types anything, while {@code validation}
 * answers "is what the user typed acceptable" and is read afterwards. Keeping them apart is what
 * stops the UI from offering {@code >} on a type the backend can only ever compare to false.
 */
@Getter
@Builder
@Schema(
    description =
        "Operator capabilities and value format rules of a primitive chaining type, so the UI can"
            + " offer the right operators and validate values without duplicating backend rules.")
public class PrimitiveTypeDescriptorOutput {

  @Schema(description = "The primitive type this descriptor applies to.")
  @JsonProperty("primitive_type")
  private PrimitiveType primitiveType;

  @Schema(description = "What the user can do with values of this type.")
  @JsonProperty("primitive_type_capabilities")
  private PrimitiveTypeCapabilitiesOutput capabilities;

  @Schema(description = "How a value of this type is validated.")
  @JsonProperty("primitive_type_validation")
  private PrimitiveTypeValidationOutput validation;
}
