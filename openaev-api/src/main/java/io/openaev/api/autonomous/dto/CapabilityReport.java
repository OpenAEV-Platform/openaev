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

  /**
   * The real execution substrate the orchestrator can build on, so it stops guessing where a
   * crafted payload runs. Grounds the "detect -> exploit" step: is an HTTP-request injector
   * actually available (web payloads), is there a live agent on an endpoint (command payloads), and
   * if neither, what should the operator enable/add. Optional/additive: null on older payloads.
   */
  @JsonProperty("arsenal")
  @Schema(
      description = "Installed injectors and the delivery substrate available for custom actions")
  private ArsenalInventory arsenal;

  /**
   * A snapshot of what the tenant can actually execute right now: which injectors are installed
   * (and whether they look active), and whether a custom web or command payload has anywhere to
   * run.
   */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Installed arsenal + delivery substrate for custom actions")
  public static class ArsenalInventory {

    @JsonProperty("installed_injectors")
    @Schema(description = "Injectors registered in this tenant's catalog")
    private List<InstalledInjector> installedInjectors;

    @JsonProperty("http_delivery_available")
    @Schema(
        description =
            "True when an HTTP-request-capable injector is installed and active - i.e. a raw HTTP"
                + " exploit payload (SQLi, SSRF, path traversal, auth bypass) has somewhere to run")
    private boolean httpDeliveryAvailable;

    @JsonProperty("command_delivery_available")
    @Schema(
        description =
            "True when at least one agent/implant is active - i.e. a Command payload has a host to"
                + " execute on")
    private boolean commandDeliveryAvailable;

    @JsonProperty("active_agent_count")
    @Schema(description = "Number of active agents/implants (last seen within the active window)")
    private int activeAgentCount;

    @JsonProperty("advice")
    @Schema(description = "Deterministic one-line recommendation on how to obtain a substrate")
    private String advice;
  }

  /** One injector registered in the tenant catalog and whether it currently looks active. */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "An installed injector and its activity state")
  public static class InstalledInjector {

    @JsonProperty("injector_type")
    private String injectorType;

    @JsonProperty("name")
    private String name;

    @JsonProperty("active")
    @Schema(description = "Built-in injectors are always active; external ones must heartbeat")
    private boolean active;

    @JsonProperty("payloads")
    @Schema(description = "True when the injector can carry custom payloads")
    private boolean payloads;
  }
}
