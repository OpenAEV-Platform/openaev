package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.service.scenario.revision.ScenarioMerge.Choice;
import java.util.Map;

/** A release or a revision to apply, with a side (or a typed value) for each conflict. */
public record ScenarioUpdateInput(
    @JsonProperty("version") String version,
    @JsonProperty("revision_id") String revisionId,
    @JsonProperty("choices") Map<String, Choice> choices,
    @JsonProperty("edits") Map<String, JsonNode> edits,
    @JsonProperty("local_hash") String localHash) {}
