package io.openaev.service.attackpath.ingestion;

/**
 * One resolved attack-path edge (source → target) for an executed inject: the #203 slice of the
 * EXECUTION row — the source/target framing plus the asset/agent attributes frozen at run time. The
 * kind literals match what the graph read branches on ({@code INJECTOR} source; {@code ASSET} /
 * {@code DISCOVERED} target).
 */
public record ResolvedExecutionEdge(
    String sourceKind, // "INJECTOR" or "ASSET" (an agent's endpoint)
    String sourceInjector, // the injector name, when the source is an injector
    String sourceAssetId, // the agent endpoint's asset id, when the source is an asset
    String sourceHostname, // the source endpoint's frozen hostname (agent-based only)
    String sourceIp, // the source endpoint's frozen ip (agent-based only)
    String sourcePlatform, // the source endpoint's frozen platform (agent-based only)
    String targetKind, // "ASSET" or "DISCOVERED"
    String targetAssetId,
    String targetRawValue,
    String targetKey, // coalesce(targetAssetId, targetRawValue)
    String targetHostname,
    String targetIp,
    String targetPlatform,
    String agentId,
    String agentName,
    String agentPrivilege) {}
