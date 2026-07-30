package io.openaev.service.attackpath.dto;

import java.util.List;

/**
 * Endpoint relations response (issue 6647): the executions targeting one endpoint (as feed nodes)
 * and the grouped edges into it, built from a single indexed read. This is what a click on an
 * endpoint focuses on, and it expands the {@code +N} grouped edge.
 *
 * <p>To keep it a single query (no finding join), the execution feed nodes here do not carry {@code
 * findingsNodeIds} — that cross-reference is on the full graph's feed. The front already has the
 * graph when it clicks an endpoint.
 */
public record AttackPathEndpointRelationsDTO(
    List<AttackPathNodeDTO> executions,
    List<AttackPathEdges> edges,
    /**
     * How many executions target this endpoint in total. The {@code executions} list is one page of
     * them; the edges are always whole, so the client knows how much is left without a second read.
     */
    long totalExecutions) {}
