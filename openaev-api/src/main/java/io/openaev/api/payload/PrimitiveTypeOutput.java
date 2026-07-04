package io.openaev.api.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.PrimitiveType;
import jakarta.validation.constraints.NotNull;

public record PrimitiveTypeOutput(@JsonProperty("argument_type") @NotNull PrimitiveType type) {}
