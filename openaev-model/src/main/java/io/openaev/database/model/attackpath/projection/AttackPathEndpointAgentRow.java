package io.openaev.database.model.attackpath.projection;

/**
 * One agent name seen on one endpoint (issue 6647), from a {@code GROUP BY target_key, agent_name}.
 * Feeds the delta's recompute of an endpoint node's agent list: that list is a property of ALL the
 * endpoint's executions, so a delta carrying a subset of rows cannot derive it and has to read it
 * back over the whole endpoint.
 */
public record AttackPathEndpointAgentRow(String targetKey, String agentName) {}
