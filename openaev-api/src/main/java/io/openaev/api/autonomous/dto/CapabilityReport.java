package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The answer to a {@link CapabilityQueryInput}: one {@link CapabilityResolution} per queried token,
 * plus a rolled-up view of what is missing. The orchestrator reads {@code gaps} to decide when to
 * narrate a capability gap (and, when authorized, craft a custom arsenal item), and the UI renders
 * {@code resolutions} as the capability-gap strip.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Capability resolution report")
public class CapabilityReport {

  @JsonProperty("resolutions")
  @Schema(description = "One resolution per queried capability token")
  private List<CapabilityResolution> resolutions;

  @JsonProperty("gaps")
  @Schema(description = "Convenience subset: only the unsatisfied resolutions")
  private List<CapabilityResolution> gaps;

  @JsonProperty("fully_satisfied")
  @Schema(description = "True when every queried token has at least one installed contract")
  private boolean fullySatisfied;
}
