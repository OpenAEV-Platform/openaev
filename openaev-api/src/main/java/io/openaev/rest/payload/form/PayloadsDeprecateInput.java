package io.openaev.rest.payload.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PayloadsDeprecateInput(
    @JsonProperty("collector_id") @NotNull String collectorId,
    @JsonProperty("payload_external_ids") @NotNull List<String> processedPayloadExternalIds) {}
