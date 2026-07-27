package io.openaev.database.model.attackpath.projection;

/**
 * Flat (contract external id, ATT&CK pattern) pair for the attack-path injector nodes. A flat
 * projection rather than the {@code InjectorContract} entity on purpose: the entity drags its whole
 * eager graph (patterns, domains, tags, links) into secondary selects, whereas this one join
 * returns exactly what the injector node needs in a single query.
 */
public record AttackPathInjectorPatternRow(
    String contractExternalId, String patternExternalId, String patternName) {}
