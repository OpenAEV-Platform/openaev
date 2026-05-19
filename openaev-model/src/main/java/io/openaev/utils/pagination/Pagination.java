package io.openaev.utils.pagination;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Data
public class Pagination {
  @Schema(description = "Page number to get")
  @NotNull
  @Min(0)
  int page = 0;

  @Schema(description = "Element number by page")
  @NotNull
  @Max(1000)
  int size = 20;
}
