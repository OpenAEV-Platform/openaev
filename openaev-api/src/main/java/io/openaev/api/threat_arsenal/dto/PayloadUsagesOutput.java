package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** The usages of a payload version the user can read, and how many others it cannot. */
public record PayloadUsagesOutput(
    @JsonProperty("payload_usages") @NotNull List<PayloadUsageOutput> usages,
    @JsonProperty("payload_usages_hidden_count") int hiddenCount) {}
