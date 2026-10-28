package io.openaev.api.scenario;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Author facet row of the Scenarios screen: an author and its scenario count under the filters. */
public record ScenarioAuthorCountOutput(
    @JsonProperty("author") @Schema(description = "Author id (user or organization)") String author,
    @JsonProperty("author_name") @Schema(description = "Author display name") String authorName,
    @JsonProperty("author_type") @Schema(description = "Author type: user or organization")
        String authorType,
    @JsonProperty("count") @Schema(description = "Scenarios by this author under the filters")
        Long count) {}
