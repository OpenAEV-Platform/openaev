package io.openaev.engine.security;

import io.openaev.database.raw.RawUserAuth;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.model.inject.EsInject;
import io.openaev.engine.model.scenario.EsScenario;
import io.openaev.engine.model.simulation.EsSimulation;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What one reader may see of the search engine, resolved per request from the source of truth
 * (capabilities and grants through {@code PermissionService}, markings through the same clearance
 * as the SQL rewrite). The engine only applies it as id filters: no security rule is copied into
 * the queries, and nothing security-related is stored in the indexed documents.
 *
 * <p>Three dimensions, applied next to the tenant filter on every read:
 *
 * <ul>
 *   <li>{@code readableEntities}: the {@code base_entity} values the reader may read at all
 *       (capability per document type). An entity absent from the set is excluded.
 *   <li>{@code grantFilters}: for a grant-managed entity the reader holds no capability for, the
 *       fields that link a document to a granted resource and the granted ids. A document is kept
 *       when one of these fields holds one of these ids. An entity absent from the map has no grant
 *       restriction.
 *   <li>{@code hiddenAssetIds}: the assets outside the reader's clearance. Documents about them are
 *       excluded, and their ids are removed from aggregations and from returned documents.
 * </ul>
 *
 * @param unrestricted the reader sees everything in its tenants (admin, BYPASS, or an engine-wide
 *     system read): no filter at all
 * @param readableEntities {@code base_entity} values the reader may read
 * @param grantFilters per entity, the grant keys a document must match (OR)
 * @param hiddenAssetIds assets outside the reader's clearance
 * @param legacyGrantUser the reader as {@code RawUserAuth}, only for the engines not ported yet
 *     (es9, OpenSearch), whose snapshot export still applies the former grant filter; {@code null}
 *     when that export skips it
 */
public record EngineReadScope(
    boolean unrestricted,
    Set<String> readableEntities,
    Map<String, List<GrantKey>> grantFilters,
    Set<String> hiddenAssetIds,
    @Nullable RawUserAuth legacyGrantUser) {

  /**
   * One way a document links to a granted resource.
   *
   * @param field the indexed field holding the resource id (e.g. {@code base_simulation_side})
   * @param ids the ids of the resources the reader is granted on, through this field
   */
  public record GrantKey(String field, Set<String> ids) {
    public GrantKey {
      ids = Set.copyOf(ids);
    }
  }

  public EngineReadScope {
    readableEntities = Set.copyOf(readableEntities);
    grantFilters = Map.copyOf(grantFilters);
    hiddenAssetIds = Set.copyOf(hiddenAssetIds);
  }

  /** A reader who sees everything of the tenants in its {@code TxCtx}: no per-user filter. */
  public static EngineReadScope unrestricted(@Nullable RawUserAuth legacyGrantUser) {
    return new EngineReadScope(true, Set.of(), Map.of(), Set.of(), legacyGrantUser);
  }

  /**
   * Removes hidden asset ids from a returned document. A parent (inject, scenario, simulation)
   * stays visible when it targets a hidden asset among others; only the reference goes. Documents
   * about a hidden asset never get here: the query already excluded them.
   *
   * @return the same document, for use in a stream
   */
  public <T extends EsBase> T stripHiddenAssets(T document) {
    if (unrestricted || hiddenAssetIds.isEmpty() || document == null) {
      return document;
    }
    switch (document) {
      case EsInject inject -> inject.setBase_assets_side(visible(inject.getBase_assets_side()));
      case EsScenario scenario ->
          scenario.setBase_assets_side(visible(scenario.getBase_assets_side()));
      case EsSimulation simulation ->
          simulation.setBase_assets_side(visible(simulation.getBase_assets_side()));
      default -> {
        // no asset reference to strip
      }
    }
    return document;
  }

  private Set<String> visible(Set<String> assetIds) {
    if (assetIds == null) {
      return null;
    }
    return assetIds.stream().filter(id -> !hiddenAssetIds.contains(id)).collect(Collectors.toSet());
  }
}
