package io.openaev.service.attackpath.dto;

/**
 * Top-bar counters, derived in the rebuild pass (issue 6647). {@code endpoints} is the number of
 * distinct execution targets (from Read A); the rest are distinct findings by type (from Read B).
 * {@code shares} counts distinct SMB {@code share} findings under their stored type: a share is a
 * complex finding (host, share name, permissions), so it is never folded into another type.
 */
public record AttackPathCounters(
    long endpoints, long credentials, long users, long cves, long ports, long shares) {}
