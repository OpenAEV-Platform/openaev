package io.openaev.database.model.attackpath.projection;

import java.time.Instant;

/**
 * Flat projection for Read A of the attack-path rebuild: the short display columns of one {@code
 * attackpath_execution} row. Deliberately omits {@code command} and {@code terminal_output} so the
 * graph read never pulls the heavy (TOAST) columns.
 */
public record AttackPathExecutionRow(
    String id,
    String sourceKind,
    String sourceAssetId,
    String agentId,
    String agentName,
    String agentPrivilege,
    String sourceInjector,
    String targetKind,
    String targetAssetId,
    String targetRawValue,
    String targetKey,
    String targetHostname,
    String targetIp,
    String targetPlatform,
    String payloadName,
    Instant executedAt,
    String preventionStatus,
    String detectionStatus,
    String vulnerabilityStatus,
    String stepTemplateId,
    // Injector node enrichment: the frozen contract external id resolves the ATT&CK techniques, and
    // the injector type labels the node with what actually ran.
    String contractExternalId,
    String injectorType,
    // Source endpoint attributes, frozen at run time, so a source-only endpoint (never a target)
    // still renders its hostname/ip/platform instead of a bare id.
    String sourceHostname,
    String sourceIp,
    String sourcePlatform) {}
