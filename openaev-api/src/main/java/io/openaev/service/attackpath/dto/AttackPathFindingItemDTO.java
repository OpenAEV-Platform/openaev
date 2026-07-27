package io.openaev.service.attackpath.dto;

import java.util.List;

/**
 * One row of a finding-widget drawer (issue 5048): a finding on an endpoint, with the producing
 * execution ids so the front can cross-focus the map edge and the execution feed. {@code value} is
 * masked server-side for the credentials category. {@code endpointNodeId} is the deterministic map
 * node id of the endpoint (from {@code AttackPathIds}), so the front can centre and highlight it.
 */
public record AttackPathFindingItemDTO(
    String type,
    String value,
    String endpointKey,
    String endpointNodeId,
    List<String> executionIds,
    AttackPathFindingVerdictsDTO verdicts) {}
