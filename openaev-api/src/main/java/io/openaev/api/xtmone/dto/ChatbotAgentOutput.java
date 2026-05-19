package io.openaev.api.xtmone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Output DTO for a single XTM One chatbot agent surfaced through {@code /api/chatbot/agents}.
 * Mirrors the subset of fields the frontend needs to render agent pickers.
 */
public record ChatbotAgentOutput(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("slug") String slug,
    @JsonProperty("description") String description) {}
