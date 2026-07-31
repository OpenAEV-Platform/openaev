package io.openaev.service.attackpath.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A simulation's attack-path version moved (#6647, spec 003, FR1): the nudge that tells an open
 * attack-path view to fetch its delta now instead of waiting for the next safety-net poll.
 *
 * <p>Published by {@link AttackPathVersionService#bump} inside the writer's transaction and
 * delivered after commit, so a client fetching on the nudge can never observe a version lower than
 * the announced one. It is a notification, never state: the delta read stays the only source of
 * graph data, which is why a lost, duplicated or reordered nudge cannot corrupt a client.
 *
 * <p>{@code tenantId} is a routing field for the stream's per-consumer tenant check and is {@link
 * JsonIgnore}d so it never reaches the wire (a tenant id is never part of a response). The wire
 * payload is therefore exactly {@code {simulation_id, version}}.
 */
public record AttackPathVersionEvent(
    @JsonProperty("simulation_id") String simulationId,
    @JsonIgnore String tenantId,
    @JsonProperty("version") long version) {}
