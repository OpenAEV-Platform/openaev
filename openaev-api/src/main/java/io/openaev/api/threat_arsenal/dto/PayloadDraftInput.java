package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/** What the editor saves in a draft: the action's fields, possibly incomplete. */
public record PayloadDraftInput(@JsonProperty("draft_content") @NotNull JsonNode content) {}
