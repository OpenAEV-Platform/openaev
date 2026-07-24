package io.openaev.service.attackpath.dto;

import java.util.List;

/**
 * Endpoint expand response (issue 6647): the finding-type nodes and finding nodes discovered on one
 * endpoint, built from a single indexed read. The front adds them to the map under the endpoint;
 * the edges are derivable from the nodes' deterministic ids ({@code assetNodeId}/{@code
 * findingsTypeNodeId}).
 */
public record AttackPathExpandDTO(
    List<AttackPathNodeDTO> findingTypes, List<AttackPathNodeDTO> findings) {}
