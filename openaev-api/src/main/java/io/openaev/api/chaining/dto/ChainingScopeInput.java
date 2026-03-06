package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Input scope payload for chaining configuration.")
public class ChainingScopeInput {

  @Valid
  @Schema(description = "List scope rules.")
  @JsonProperty("scope_rules")
  private List<ChainingScopeRuleInput> scopeRules;
}
