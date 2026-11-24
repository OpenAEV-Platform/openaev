package io.openaev.api.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** The starting-points rail of the graph landing, every section in one read. */
public record GraphStartingPointsOutput(
    @JsonProperty("starting_points") @NotNull List<GraphStartingPointOutput> points) {}
