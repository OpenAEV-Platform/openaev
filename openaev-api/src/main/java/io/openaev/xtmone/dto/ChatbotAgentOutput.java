package io.openaev.xtmone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record ChatbotAgentOutput(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("slug") String slug,
    @JsonProperty("description") String description) {}
