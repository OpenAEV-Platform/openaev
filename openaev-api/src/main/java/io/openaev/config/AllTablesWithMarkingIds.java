package io.openaev.config;

import java.util.Set;

/**
 * Every table the current schema marks (has a {@code marking_ids} array column), unconditionally —
 * distinct from {@link MarkedTables}, which the marking dimension narrows to the {@code
 * openaev.enabled-dev-features}/{@code openaev.marking.active-tables} allowlist for read filtering.
 *
 * <p>A marking write is not feature-gated: {@code AssetMarkingsApi} and {@code
 * TenantGroupApi.updateGroupMarkings} accept and persist a {@code marking_ids} value regardless of
 * whether the flag is on or the table is in the activation allowlist. Deleting a {@code
 * MarkingDefinition} must therefore scrub its id out of <b>every</b> such column, unconditionally —
 * otherwise a definition deleted while a table is inactive leaves a dangling id that survives
 * silently, and resurfaces the moment the table is later activated: rows carrying it become hidden
 * from every non-admin caller, with no error and no way back through the API.
 */
public record AllTablesWithMarkingIds(MarkedTables tables) {

  public Set<String> tableNames() {
    return tables.tableNames();
  }
}
