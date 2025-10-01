package io.openaev.rest.helper;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
class ValidationContent {
  @Schema(description = "A list of errors")
  private List<String> errors;

  public ValidationContent(String error) {
    this.errors = List.of(error);
  }
}

@Data
class ValidationError {
  @Schema(description = "Map of errors by input")
  private Map<String, ValidationContent> children;
}

@Data
public class ValidationErrorBag {
  @Schema(description = "Return code")
  private int code = 400;

  @Schema(description = "Return message")
  private String message = "Validation Failed";

  @Schema(description = "Errors raised")
  private ValidationError errors;

  public ValidationErrorBag() {
    // Default constructor
  }

  public ValidationErrorBag(int code, String message) {
    this.code = code;
    this.message = message;
  }
}
