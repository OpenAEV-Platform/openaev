package io.openaev.api.markings.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Note the absence of {@code tenant_id}: the marking catalogue is tenant-scoped, but the tenant is
 * never exposed to the client.
 */
public record MarkingDefinitionOutput(
    @JsonProperty(ALIAS_ID) @NotBlank String id,
    @JsonProperty(ALIAS_TYPE) @NotBlank String type,
    @JsonProperty(ALIAS_NAME) @NotBlank String name,
    @JsonProperty(ALIAS_ORDER) @NotNull Integer order,
    @JsonProperty(ALIAS_COLOR) String color) {

  public static final String ALIAS_ID = "marking_id";
  public static final String ALIAS_TYPE = "marking_type";
  public static final String ALIAS_NAME = "marking_name";
  public static final String ALIAS_ORDER = "marking_order";
  public static final String ALIAS_COLOR = "marking_color";
}
