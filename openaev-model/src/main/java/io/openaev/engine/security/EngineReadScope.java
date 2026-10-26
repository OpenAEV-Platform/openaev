package io.openaev.engine.security;

import io.openaev.database.raw.RawUserAuth;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.model.finding.EsFinding;
import io.openaev.engine.model.inject.EsInject;
import io.openaev.engine.model.injectexpectation.EsInjectExpectation;
import io.openaev.engine.model.scenario.EsScenario;
import io.openaev.engine.model.simulation.EsSimulation;
import io.openaev.engine.model.snapshotobservation.EsAttackObservation;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * What one reader may see of the search engine, resolved per request from the source of truth
 * (capabilities and grants through {@code PermissionService}, markings through the same clearance
 * as the SQL rewrite). The engine only applies it as id filters: no security rule is copied into
 * the queries, and nothing security-related is stored in the indexed documents.
 *
 * <p>Four dimensions, all held per tenant in {@code tenantRules}: a request can read several
 * tenants, and the reader's capabilities, grants and the assets it may not see differ from one
 * tenant to another, so each tenant's rules only apply to the documents of that tenant ({@code
 * base_tenant_side}).
 *
 * @param unrestricted the reader sees everything in its tenants (admin, BYPASS, or an engine-wide
 *     system read): no filter at all
 * @param tenantRules per tenant id, what the reader may read in that tenant
 * @param legacyGrantUser the reader as {@code RawUserAuth}, for the former grant-only filter of the
 *     snapshot export: applied by every engine on an unrestricted scope (the {@code MARKING}
 *     preview feature is off), and by the engines not ported yet (es9, OpenSearch) on any scope;
 *     {@code null} when that export skips it
 */
public record EngineReadScope(
    boolean unrestricted,
    Map<String, TenantRules> tenantRules,
    @Nullable RawUserAuth legacyGrantUser) {

  /**
   * What the reader may read in one tenant.
   *
   * <ul>
   *   <li>{@code readableEntities}: the {@code base_entity} values the reader may read at all
   *       (capability per document type). An entity absent from the set is excluded.
   *   <li>{@code grantFilters}: for a grant-managed entity the reader holds no capability for, the
   *       fields that link a document to a granted resource and the granted ids. A document is kept
   *       when one of these fields holds one of these ids. An entity absent from the map has no
   *       grant restriction.
   *   <li>{@code hiddenAssetIds}: the assets of this tenant outside the reader's clearance.
   *       Documents about them are excluded, and their ids are removed from aggregations and from
   *       returned documents.
   * </ul>
   *
   * @param unrestricted every type is readable, without grant filter (hidden assets still apply)
   * @param readableEntities {@code base_entity} values the reader may read
   * @param grantFilters per entity, the grant keys a document must match (OR)
   * @param hiddenAssetIds assets of this tenant outside the reader's clearance
   */
  public record TenantRules(
      boolean unrestricted,
      Set<String> readableEntities,
      Map<String, List<GrantKey>> grantFilters,
      Set<String> hiddenAssetIds) {

    /** Every type readable, no grant filter, no hidden asset. */
    public static final TenantRules ALL = new TenantRules(true, Set.of(), Map.of(), Set.of());

    public TenantRules {
      readableEntities = Set.copyOf(readableEntities);
      grantFilters = Map.copyOf(grantFilters);
      hiddenAssetIds = Set.copyOf(hiddenAssetIds);
    }

    /** Nothing to filter in this tenant. */
    public boolean seesEverything() {
      return unrestricted && hiddenAssetIds.isEmpty();
    }
  }

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
    tenantRules = Map.copyOf(tenantRules);
  }

  /** A reader who sees everything of the tenants in its {@code TxCtx}: no per-user filter. */
  public static EngineReadScope unrestricted(@Nullable RawUserAuth legacyGrantUser) {
    return new EngineReadScope(true, Map.of(), legacyGrantUser);
  }

  /** A reader of one tenant, with these rules there. */
  public static EngineReadScope forTenant(
      String tenantId,
      Set<String> readableEntities,
      Map<String, List<GrantKey>> grantFilters,
      Set<String> hiddenAssetIds) {
    return new EngineReadScope(
        false,
        Map.of(tenantId, new TenantRules(false, readableEntities, grantFilters, hiddenAssetIds)),
        null);
  }

  /**
   * The hidden assets of every tenant of the scope. For what is not bound to one tenant: the
   * buckets of an aggregation mix the tenants of the request, and a returned document is stripped
   * of any hidden asset id (asset ids are unique, so the union cannot hide another tenant's asset).
   */
  public Set<String> hiddenAssetIds() {
    if (tenantRules.size() == 1) {
      return tenantRules.values().iterator().next().hiddenAssetIds();
    }
    return tenantRules.values().stream()
        .flatMap(rules -> rules.hiddenAssetIds().stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * The rules for documents that carry no tenant ({@code base_tenant_side} absent): only what every
   * tenant of the scope allows, without grant filter, and without any tenant's hidden assets. A
   * type grant-filtered in one tenant is excluded, since such a document cannot be tied to a
   * granted resource of a tenant.
   */
  public TenantRules sharedRules() {
    if (tenantRules.isEmpty()) {
      return new TenantRules(false, Set.of(), Map.of(), Set.of());
    }
    if (tenantRules.values().stream().allMatch(TenantRules::unrestricted)) {
      return new TenantRules(true, Set.of(), Map.of(), hiddenAssetIds());
    }
    Set<String> shared = new HashSet<>(EngineSecurityRegistry.entities().keySet());
    for (TenantRules rules : tenantRules.values()) {
      if (!rules.unrestricted()) {
        shared.retainAll(rules.readableEntities());
        shared.removeAll(rules.grantFilters().keySet());
      }
    }
    return new TenantRules(false, shared, Map.of(), hiddenAssetIds());
  }

  /**
   * Removes hidden asset ids from a returned document. A parent (inject, scenario, simulation)
   * stays visible when it targets a hidden asset among others, a finding stays visible when one of
   * its endpoints is visible, and an expectation or observation on a visible asset stays visible
   * when a hidden security platform reported on it; only the hidden references go, from the
   * reference fields and from {@code base_dependencies}. Documents about a hidden asset only never
   * get here: the query already excluded them.
   *
   * @return the same document, for use in a stream
   */
  public <T extends EsBase> T stripHiddenAssets(T document) {
    return this.<T>assetStripper().apply(document);
  }

  /**
   * {@link #stripHiddenAssets} for a page of documents: the hidden assets of the scope are
   * collected once, not per document.
   */
  public <T extends EsBase> UnaryOperator<T> assetStripper() {
    if (unrestricted) {
      return UnaryOperator.identity();
    }
    Set<String> hiddenAssetIds = hiddenAssetIds();
    if (hiddenAssetIds.isEmpty()) {
      return UnaryOperator.identity();
    }
    return document -> strip(document, hiddenAssetIds);
  }

  private static <T extends EsBase> T strip(T document, Set<String> hiddenAssetIds) {
    if (document == null) {
      return null;
    }
    // A finding depends on each of its endpoints, so its dependencies name them too.
    if (document.getBase_dependencies() != null) {
      document.setBase_dependencies(
          document.getBase_dependencies().stream()
              .filter(id -> !hiddenAssetIds.contains(id))
              .collect(Collectors.toCollection(ArrayList::new)));
    }
    switch (document) {
      case EsInject inject ->
          inject.setBase_assets_side(visible(inject.getBase_assets_side(), hiddenAssetIds));
      case EsScenario scenario ->
          scenario.setBase_assets_side(visible(scenario.getBase_assets_side(), hiddenAssetIds));
      case EsSimulation simulation ->
          simulation.setBase_assets_side(visible(simulation.getBase_assets_side(), hiddenAssetIds));
      case EsFinding finding ->
          finding.setBase_endpoint_side(visible(finding.getBase_endpoint_side(), hiddenAssetIds));
      case EsInjectExpectation expectation ->
          expectation.setBase_security_platforms_side(
              visible(expectation.getBase_security_platforms_side(), hiddenAssetIds));
      case EsAttackObservation observation -> {
        observation.setBase_security_platforms_side(
            visible(observation.getBase_security_platforms_side(), hiddenAssetIds));
        observation.setAttack_observation_platforms_succeeded(
            visible(observation.getAttack_observation_platforms_succeeded(), hiddenAssetIds));
      }
      default -> {
        // no asset reference to strip
      }
    }
    return document;
  }

  private static Set<String> visible(Set<String> assetIds, Set<String> hiddenAssetIds) {
    if (assetIds == null) {
      return null;
    }
    return assetIds.stream().filter(id -> !hiddenAssetIds.contains(id)).collect(Collectors.toSet());
  }
}
