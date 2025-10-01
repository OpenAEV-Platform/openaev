package io.openaev.rest.helper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ViolationErrorBag {

  @Schema(description = "The type of error")
  private String type;

  @Schema(description = "The message of the error")
  private String message;

  @Schema(description = "The error")
  private String error;
}
