package io.openaev.api.finding;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.FindingLocationType;
import java.time.Instant;
import lombok.Builder;

@Builder
public record FindingLocationOutput(
    @JsonProperty("finding_location") String location,
    @JsonProperty("finding_location_type") FindingLocationType locationType,
    @JsonProperty("finding_location_key") String locationKey,
    @JsonProperty("finding_location_first_seen") Instant firstSeen,
    @JsonProperty("finding_location_last_seen") Instant lastSeen,
    @JsonProperty("finding_location_occurrences") long occurrences,
    @JsonProperty("finding_location_severity") String severity) {}
