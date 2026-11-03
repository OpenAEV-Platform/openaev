package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.catalog.CatalogInstallPreviewOutput.InjectPreview;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Target state of each inject of a scenario: what the drawer shows before a launch (F12). */
public record ScenarioTargetsOutput(
    @JsonProperty("injects") @NotNull List<InjectPreview> injects) {}
