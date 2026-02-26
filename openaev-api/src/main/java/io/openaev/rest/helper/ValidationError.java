package io.openaev.rest.helper;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
class ValidationError {
  @Schema(description = "Map of errors by input")
  private Map<String, ValidationContent> children;
}
