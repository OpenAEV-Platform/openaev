package io.openaev.service.attackpath.dto;

/**
 * One MITRE ATT&amp;CK technique of the execution's injector contract (issue 5048): its external id
 * (for example {@code T1046}) and its name, for the technique chips in the execution detail drawer.
 */
public record AttackPathAttackPatternDTO(String externalId, String name) {}
