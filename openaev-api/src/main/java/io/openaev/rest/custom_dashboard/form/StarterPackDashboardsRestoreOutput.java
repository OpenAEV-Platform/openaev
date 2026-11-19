package io.openaev.rest.custom_dashboard.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Result of a restore of the bundled starter-pack dashboards. */
@Schema(description = "Starter-pack dashboards restored and tenant defaults set back")
public record StarterPackDashboardsRestoreOutput(
    @Schema(description = "Names of the dashboards created by this restore")
        @JsonProperty("restored_dashboards")
        @NotNull
        List<String> restoredDashboards,
    @Schema(description = "Names of the starter-pack dashboards that already existed")
        @JsonProperty("existing_dashboards")
        @NotNull
        List<String> existingDashboards,
    @Schema(description = "Tenant default dashboard settings that were empty and are set back")
        @JsonProperty("defaults_set")
        @NotNull
        List<String> defaultsSet) {}
