package io.openaev.service.attackpath.dto;

/**
 * Top-bar counters, derived in the rebuild pass (issue 6647). {@code endpoints} is the number of
 * distinct execution targets (from Read A); the rest are distinct findings by type (from Read B).
 * There is no files counter: no {@code ContractOutputType} value maps to it.
 */
public record AttackPathCounters(
    long endpoints, long credentials, long users, long cves, long ports) {}
