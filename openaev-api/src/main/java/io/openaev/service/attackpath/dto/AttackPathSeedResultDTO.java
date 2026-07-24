package io.openaev.service.attackpath.dto;

/**
 * Result of a seed run (issue 6647): how many rows the generator inserted and how long it took. The
 * counts are what the benchmark and the DoD verify against.
 */
public record AttackPathSeedResultDTO(
    long simulations, long executions, long findings, long elapsedMs) {}
