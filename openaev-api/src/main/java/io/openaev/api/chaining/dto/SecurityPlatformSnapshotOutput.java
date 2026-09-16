package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ScopeRuleSnapshotStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(
    description =
        "Connected security platform of a launched simulation, shown as its current effective "
            + "frozen photo (end snapshot once the run is over, launch snapshot while running) "
            + "plus its computed change status.")
public class SecurityPlatformSnapshotOutput {

  @Schema(description = "Frozen security-platform id (a new id signals a reinstall).")
  @JsonProperty("security_platform_snapshot_id")
  private String id;

  @Schema(description = "Frozen security-platform name.")
  @JsonProperty("security_platform_snapshot_name")
  private String name;

  @Schema(description = "Security-platform type (e.g. EDR / SIEM).")
  @JsonProperty("security_platform_snapshot_type")
  private String type;

  @Schema(description = "Frozen last-modified date (a later value signals a reconfiguration).")
  @JsonProperty("security_platform_snapshot_updated_at")
  private Instant updatedAt;

  @Schema(description = "Computed change status of this platform vs the frozen snapshots.")
  @JsonProperty("security_platform_snapshot_status")
  private ScopeRuleSnapshotStatus status;
}
