package io.openaev.rest.helper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ValidationErrorBag {
  @Schema(description = "Return code")
  private int code = 400;

  @Schema(description = "Return message")
  private String message = "Validation Failed";

  @Schema(description = "Errors raised")
  private ValidationError errors;

  public ValidationErrorBag(int code, String message) {
    this.code = code;
    this.message = message;
  }
}
