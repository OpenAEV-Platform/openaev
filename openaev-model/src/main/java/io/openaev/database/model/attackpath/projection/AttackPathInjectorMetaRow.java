package io.openaev.database.model.attackpath.projection;

/**
 * Per-injector metadata for the collapsed graph: the injector name plus its frozen contract
 * external id and type. The collapsed edges are grouped by source, so they cannot carry these
 * without fragmenting the groups; this small distinct read supplies them instead.
 */
public record AttackPathInjectorMetaRow(
    String sourceInjector, String contractExternalId, String injectorType) {}
