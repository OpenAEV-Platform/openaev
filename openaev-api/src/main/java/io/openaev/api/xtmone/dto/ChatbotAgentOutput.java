package io.openaev.api.xtmone.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Output DTO for a single XTM One chatbot agent surfaced Mirrors the subset of fields the frontend
 * needs to render agent pickers.
 */
public record ChatbotAgentOutput(
    @JsonProperty("id") @JsonAlias("agent_id") String id,
    @JsonProperty("name") @JsonAlias("agent_name") String name,
    @JsonProperty("slug") @JsonAlias("agent_slug") String slug,
    @JsonProperty("description") @JsonAlias("agent_description") String description) {}
