package io.openaev.service.engine;

import io.openaev.annotation.AllowRawJdbc;
import io.openaev.config.MarkedTables;
import io.openaev.context.MarkingCtx;
import io.openaev.context.MarkingScopeSupplier;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Grant;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawGrant;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.database.repository.UserRepository;
import io.openaev.engine.security.EngineReadScope;
import io.openaev.engine.security.EngineSecurityRegistry;
import io.openaev.engine.security.EngineSecurityRegistry.EntitySecurity;
import io.openaev.engine.security.EngineSecurityRegistry.ResourceAccess;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PermissionService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.UserService;
import io.openaev.utils.mapper.RawUserAuthMapper;
import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

/**
 * Resolves what the current reader may see of the search engine, from the source of truth: the
 * capabilities through {@link PermissionService}, the grants of the user, and the marking clearance
 * the SQL rewrite uses for the same request. The engine only applies the result as id filters; no
 * rule is re-implemented in the queries.
 *
 * <p>Resolved per request, never cached: a capability, grant or marking change applies on the next
 * read, and the documents already indexed (the history of past simulations) are covered by the same
 * ids.
 *
 * <p>Behind the {@link PreviewFeature#MARKING} preview feature as a whole: with the feature off,
 * every reader is unrestricted and dashboards keep the tenant filter only, as before.
 */
@Service
@AllowRawJdbc(
    reason =
        "lists the marked assets outside the reader's clearance: the statement inspector would hide"
            + " exactly those rows from an ORM query, so the lookup must not go through it. Reads"
            + " asset ids only, scoped to the tenants of the request.")
public class EngineReadScopeResolver {

  /**
   * Marked assets of the tenants in scope whose markings are not all held: the negation of {@code
   * is_marking_set_allowed}, against the same clearance.
   */
  static final String HIDDEN_ASSETS_SQL =
      "SELECT a.asset_id, a.tenant_id FROM assets a"
          + " WHERE a.tenant_id = ANY(?)"
          + " AND cardinality(COALESCE(a.marking_ids, '{}'::text[])) > 0"
          + " AND NOT (COALESCE(a.marking_ids, '{}'::text[]) <@ ?::text[])";

  private static final String ASSETS_TABLE = "assets";

  private final PermissionService permissionService;
  private final UserService userService;
  private final UserRepository userRepository;
  private final RawUserAuthMapper rawUserAuthMapper;
  private final MarkingScopeSupplier markingScopeSupplier;
  private final MarkedTables markedTables;
  private final JdbcTemplate jdbcTemplate;
  private final PreviewFeatureService previewFeatureService;

  /**
   * Explicit constructor for {@code @Lazy}: Lombok does not copy it onto the parameters. The lazy
   * services sit high in the graph (PermissionService -> InjectService -> ...), which reaches back
   * here through AttackPatternService.
   */
  public EngineReadScopeResolver(
      @Lazy PermissionService permissionService,
      @Lazy UserService userService,
      UserRepository userRepository,
      RawUserAuthMapper rawUserAuthMapper,
      MarkingScopeSupplier markingScopeSupplier,
      MarkedTables markedTables,
      JdbcTemplate jdbcTemplate,
      @Lazy PreviewFeatureService previewFeatureService) {
    this.permissionService = permissionService;
    this.userService = userService;
    this.userRepository = userRepository;
    this.rawUserAuthMapper = rawUserAuthMapper;
    this.markingScopeSupplier = markingScopeSupplier;
    this.markedTables = markedTables;
    this.jdbcTemplate = jdbcTemplate;
    this.previewFeatureService = previewFeatureService;
  }

  /**
   * The scope of the current user for the tenants of {@code ctx}.
   *
   * @param ctx the tenant scope of the request
   * @return what the reader may see; unrestricted for everyone when {@link PreviewFeature#MARKING}
   *     is off
   */
  public EngineReadScope resolve(TxCtx ctx) {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)) {
      return EngineReadScope.unrestricted(null);
    }
    User user = userService.currentUser();
    RawUserAuth auth =
        rawUserAuthMapper.toRawUserAuth(userRepository.getUserWithAuth(user.getId()));
    if (user.isAdmin()) {
      return EngineReadScope.unrestricted(null);
    }

    // Capabilities are held per tenant, and one request can read several: each tenant's rules come
    // from the user's rights in that tenant, never from the ambient one. A scope not restricted to
    // tenants (no HTTP reader has one) gets no tenant rules: it fails closed.
    if (!(ctx instanceof TxCtx.Restricted restricted)) {
      return new EngineReadScope(false, Map.of(), auth);
    }
    Map<Grant.GRANT_RESOURCE_TYPE, Set<String>> grantedIds = grantedIdsByType(auth);
    Map<String, Set<String>> hiddenAssetIds = hiddenAssetIdsByTenant(restricted);
    Map<String, EngineReadScope.TenantRules> tenantRules = new HashMap<>();
    boolean seesEverything = true;
    boolean seesEverySimulation = true;
    for (String tenantId : restricted.tenantIds()) {
      EngineReadScope.TenantRules rules =
          rulesIn(user, tenantId, grantedIds, hiddenAssetIds.getOrDefault(tenantId, Set.of()));
      tenantRules.put(tenantId, rules);
      seesEverything &= rules.seesEverything();
      seesEverySimulation &=
          holds(user, tenantId, new ResourceAccess(ResourceType.SIMULATION, Action.READ));
    }

    // The snapshot export of the engines not ported yet still applies the former grant filter,
    // skipped when the reader sees every scenario and simulation (same rule as before).
    RawUserAuth legacyGrantUser = seesEverySimulation ? null : auth;
    if (seesEverything) {
      return EngineReadScope.unrestricted(legacyGrantUser);
    }
    return new EngineReadScope(false, tenantRules, legacyGrantUser);
  }

  /** What the user may read in one tenant, from their rights and that tenant's hidden assets. */
  private EngineReadScope.TenantRules rulesIn(
      User user,
      String tenantId,
      Map<Grant.GRANT_RESOURCE_TYPE, Set<String>> grantedIds,
      Set<String> hiddenAssetIds) {
    Set<String> readableEntities = new HashSet<>();
    Map<String, List<EngineReadScope.GrantKey>> grantFilters = new HashMap<>();
    boolean everythingReadable = true;
    for (Map.Entry<String, EntitySecurity> entry : EngineSecurityRegistry.entities().entrySet()) {
      EntitySecurity security = entry.getValue();
      if (!security.requiredAccess().stream().allMatch(access -> holds(user, tenantId, access))) {
        everythingReadable = false;
        continue;
      }
      readableEntities.add(entry.getKey());
      if (security.grantManaged() && !holds(user, tenantId, security.grantBypass())) {
        everythingReadable = false;
        List<EngineReadScope.GrantKey> keys = new ArrayList<>();
        security
            .grantKeys()
            .forEach(
                key ->
                    keys.add(
                        new EngineReadScope.GrantKey(
                            key.field(),
                            grantedIds.getOrDefault(key.grantResourceType(), Set.of()))));
        grantFilters.put(entry.getKey(), keys);
      }
    }
    return everythingReadable
        ? new EngineReadScope.TenantRules(true, Set.of(), Map.of(), hiddenAssetIds)
        : new EngineReadScope.TenantRules(false, readableEntities, grantFilters, hiddenAssetIds);
  }

  /**
   * The marked assets the reader may not see, per tenant of the request. Empty when markings are
   * not enforced on {@code assets} (preview feature off, or table not active): dashboards then show
   * what the REST API shows. The clearance is the one of the SQL rewrite for the same request; a
   * marking belongs to one tenant, so a marking held in one tenant never unhides another tenant's
   * asset.
   */
  Map<String, Set<String>> hiddenAssetIdsByTenant(TxCtx.Restricted ctx) {
    if (markedTables.get(ASSETS_TABLE) == null) {
      return Map.of();
    }
    MarkingCtx clearance = markingScopeSupplier.clearanceFor(ctx);
    List<String> held =
        clearance instanceof MarkingCtx.Restricted granted ? granted.markingIds() : List.of();
    String[] tenantIds = ctx.tenantIds().toArray(String[]::new);
    String[] markingIds = held.toArray(String[]::new);
    Map<String, Set<String>> hidden = new HashMap<>();
    jdbcTemplate.query(
        HIDDEN_ASSETS_SQL,
        statement -> {
          Array tenants = statement.getConnection().createArrayOf("varchar", tenantIds);
          Array markings = statement.getConnection().createArrayOf("text", markingIds);
          statement.setArray(1, tenants);
          statement.setArray(2, markings);
        },
        (RowCallbackHandler)
            row ->
                hidden
                    .computeIfAbsent(row.getString(2), tenantId -> new HashSet<>())
                    .add(row.getString(1)));
    return hidden;
  }

  private boolean holds(User user, String tenantId, ResourceAccess access) {
    return permissionService.hasCapabilityPermissionInTenant(
        user, tenantId, access.resourceType(), access.action());
  }

  private static Map<Grant.GRANT_RESOURCE_TYPE, Set<String>> grantedIdsByType(RawUserAuth auth) {
    if (auth == null || auth.getUser_grants() == null) {
      return Map.of();
    }
    return auth.getUser_grants().stream()
        .filter(
            grant -> grant.getGrant_resource_type() != null && grant.getGrant_resource() != null)
        .collect(
            Collectors.groupingBy(
                RawGrant::getGrant_resource_type,
                Collectors.mapping(RawGrant::getGrant_resource, Collectors.toSet())));
  }
}
