package io.openaev.service.attackpath.dto;

/**
 * Top-bar counters, derived in the rebuild pass (issue 6647). {@code endpoints} is the number of
 * distinct execution targets (from Read A); the rest are distinct findings by type (from Read B).
 * {@code files} counts distinct {@code file} findings. As an interim stand-in, SMB {@code share}
 * findings are presented as {@code file}, so today this is the distinct-share count, surfaced as a
 * real backend counter instead of the front's former {@code findingCounts.share} approximation.
 */
public record AttackPathCounters(
    long endpoints, long credentials, long users, long cves, long ports, long files) {}
