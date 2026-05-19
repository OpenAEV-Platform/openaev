package io.openaev.rest.helper;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ValidationContent {
  @Schema(description = "A list of errors")
  private List<String> errors;

  public ValidationContent(String error) {
    this.errors = List.of(error);
  }
}
