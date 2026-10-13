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
import io.openaev.service.PermissionService;
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
      "SELECT a.asset_id FROM assets a"
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

  /**
   * Explicit constructor for {@code @Lazy}: Lombok does not copy it onto the parameters. Both
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
      JdbcTemplate jdbcTemplate) {
    this.permissionService = permissionService;
    this.userService = userService;
    this.userRepository = userRepository;
    this.rawUserAuthMapper = rawUserAuthMapper;
    this.markingScopeSupplier = markingScopeSupplier;
    this.markedTables = markedTables;
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * The scope of the current user for the tenants of {@code ctx}.
   *
   * @param ctx the tenant scope of the request
   * @return what the reader may see
   */
  public EngineReadScope resolve(TxCtx ctx) {
    User user = userService.currentUser();
    RawUserAuth auth =
        rawUserAuthMapper.toRawUserAuth(userRepository.getUserWithAuth(user.getId()));
    if (user.isAdmin()) {
      return EngineReadScope.unrestricted(null);
    }

    Map<Grant.GRANT_RESOURCE_TYPE, Set<String>> grantedIds = grantedIdsByType(auth);
    Set<String> readableEntities = new HashSet<>();
    Map<String, List<EngineReadScope.GrantKey>> grantFilters = new HashMap<>();
    boolean everythingReadable = true;
    for (Map.Entry<String, EntitySecurity> entry : EngineSecurityRegistry.entities().entrySet()) {
      EntitySecurity security = entry.getValue();
      if (!security.requiredAccess().stream().allMatch(access -> holds(user, access))) {
        everythingReadable = false;
        continue;
      }
      readableEntities.add(entry.getKey());
      if (security.grantManaged() && !holds(user, security.grantBypass())) {
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

    Set<String> hiddenAssetIds = hiddenAssetIds(ctx);
    // The snapshot export of the engines not ported yet still applies the former grant filter,
    // skipped when the reader sees every scenario and simulation (same rule as before).
    RawUserAuth legacyGrantUser =
        holds(user, new ResourceAccess(ResourceType.SIMULATION, Action.READ)) ? null : auth;
    if (everythingReadable && hiddenAssetIds.isEmpty()) {
      return EngineReadScope.unrestricted(legacyGrantUser);
    }
    return new EngineReadScope(
        false, readableEntities, grantFilters, hiddenAssetIds, legacyGrantUser);
  }

  /**
   * The marked assets the reader may not see, in the tenants of the request. Empty when markings
   * are not enforced on {@code assets} (preview feature off, or table not active): dashboards then
   * show what the REST API shows.
   */
  Set<String> hiddenAssetIds(TxCtx ctx) {
    if (markedTables.get(ASSETS_TABLE) == null || !(ctx instanceof TxCtx.Restricted restricted)) {
      return Set.of();
    }
    MarkingCtx clearance = markingScopeSupplier.clearanceFor(ctx);
    List<String> held =
        clearance instanceof MarkingCtx.Restricted granted ? granted.markingIds() : List.of();
    String[] tenantIds = restricted.tenantIds().toArray(String[]::new);
    String[] markingIds = held.toArray(String[]::new);
    List<String> hidden =
        jdbcTemplate.query(
            HIDDEN_ASSETS_SQL,
            statement -> {
              Array tenants = statement.getConnection().createArrayOf("varchar", tenantIds);
              Array markings = statement.getConnection().createArrayOf("text", markingIds);
              statement.setArray(1, tenants);
              statement.setArray(2, markings);
            },
            (row, index) -> row.getString(1));
    return Set.copyOf(hidden);
  }

  private boolean holds(User user, ResourceAccess access) {
    return permissionService.hasCapabilityPermission(user, access.resourceType(), access.action());
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
