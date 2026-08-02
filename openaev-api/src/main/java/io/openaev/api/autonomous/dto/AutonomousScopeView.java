package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The authoritative, live scope of an autonomous run, read back from the run's workflow (not the
 * start-time snapshot). Splits the resolved perimeter into the allow-list (what the orchestrator
 * may attack) and the deny-list (explicit carve-outs that always win), each across every source
 * (assets, asset groups, teams, persons, and manual IP / CIDR / hostname / CSV rules). The
 * orchestrator reads this before acting so it attacks exactly what is authorized.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "An autonomous run's live, resolved scope (allow-list + deny-list)")
public class AutonomousScopeView {

  @JsonProperty("run_id")
  @Schema(description = "The autonomous run id this scope belongs to")
  private String runId;

  @JsonProperty("allowlist")
  @Schema(description = "The authorized perimeter the orchestrator may attack")
  private List<AutonomousScopeEntry> allowlist;

  @JsonProperty("denylist")
  @Schema(description = "Explicit carve-outs the orchestrator must never touch (deny wins)")
  private List<AutonomousScopeEntry> denylist;
}
