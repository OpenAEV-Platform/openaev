package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.catalog.scenario.targeting.TargetKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** An inject that takes targets, the kinds it takes and whether it has one. */
public record InjectTargetsOutput(
    @JsonProperty("inject_id") @NotBlank String id,
    @JsonProperty("inject_title") @NotBlank String title,
    @JsonProperty("inject_target_kinds") @NotNull List<TargetKind> kinds,
    @JsonProperty("inject_targeted") boolean targeted) {}
