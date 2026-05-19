package io.openaev.utils.es;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.Pagination;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.Map;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class EntitiesPaginationInput {
  @Schema(description = "Pagination to set (optional)", nullable = true)
  @JsonProperty("pagination")
  @Nullable
  Pagination pagination;

  @Schema(description = "Parameters to set")
  @JsonProperty("parameters")
  Map<String, String> parameters;
}
