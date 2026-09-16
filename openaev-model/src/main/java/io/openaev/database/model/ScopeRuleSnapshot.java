package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable, execution-time photo of a single scope rule, stored as polymorphic {@code jsonb} on
 * {@link WorkflowScopeRule}. Its shape depends on the rule's {@link ScopeRuleSource}:
 *
 * <ul>
 *   <li>{@code ASSET} / {@code ASSET_GROUP}: {@code label} + {@code assets} composition.
 *   <li>{@code MANUAL} / {@code CSV}: {@code label} is the raw value, no composition.
 *   <li>{@code SECURITY_PLATFORM}: {@code label} + a {@code securityPlatform} block ({@code id} to
 *       detect a reinstall, {@code updatedAt} to detect a reconfiguration).
 * </ul>
 *
 * See ADR-006.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScopeRuleSnapshot {

  @JsonProperty("label")
  private String label;

  /** Asset-group / asset composition; null for MANUAL / CSV / SECURITY_PLATFORM. */
  @JsonProperty("assets")
  private List<AssetSnapshot> assets;

  /** Security-platform block; null for asset / group / MANUAL / CSV rules. */
  @JsonProperty("security_platform")
  private SecurityPlatformSnapshot securityPlatform;

  /**
   * Explicit deletion marker for an end-of-run photo: the referenced target no longer resolved when
   * the run ended. The photo itself must exist (it is what marks the run as ended for the status
   * derivation), so deletion is recorded as a flag - never by degrading the label to a raw id,
   * which would read as a rename. Nullable so photos written before this flag stay untouched.
   */
  @JsonProperty("deleted")
  private Boolean deleted;

  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class AssetSnapshot {

    /** Frozen asset id, used to match an asset across launch / end / current photos. */
    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("agentsCount")
    private int agentsCount;

    /** Distinct executor names of the asset's agents (no agent ids). */
    @JsonProperty("executors")
    private List<String> executors;
  }

  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class SecurityPlatformSnapshot {

    /** Frozen platform id, detects an uninstall/reinstall (new id → different install). */
    @JsonProperty("id")
    private String id;

    /** Platform type (e.g. EDR / SIEM). */
    @JsonProperty("type")
    private String type;

    /** Frozen last-modified date, detects a reconfiguration (same id/label, later updatedAt). */
    @JsonProperty("updatedAt")
    private Instant updatedAt;
  }
}
