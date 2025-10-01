package io.openaev.jsonapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceObject(
    @NotBlank String id,
    @NotBlank String type,
    Map<String, Object> attributes,
    Map<String, Relationship> relationships) {}
