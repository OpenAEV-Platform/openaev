package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Output scope payload for chaining configuration.")
public class ChainingScopeOutput {

  @Valid
  @Schema(description = "List scope rules.")
  @JsonProperty("scope_rules")
  private List<ChainingScopeRuleOutput> scopeRules;
}
