package io.openaev.api.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ArgumentSubType;
import io.openaev.database.model.ArgumentType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * API response record representing an {@link io.openaev.database.model.ArgumentType} with its
 * associated subtypes.
 */
public record ArgumentTypeOutput(
    @JsonProperty("argument_type") @NotNull ArgumentType type,
    @JsonProperty("argument_subtypes") List<ArgumentSubType> subTypes) {}
