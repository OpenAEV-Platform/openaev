package io.openaev.engine.security;

import io.openaev.annotation.Indexable;
import io.openaev.database.model.Action;
import io.openaev.database.model.Grant.GRANT_RESOURCE_TYPE;
import io.openaev.database.model.ResourceType;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.model.asset.EsAsset;
import io.openaev.engine.model.assetgroup.EsAssetGroup;
import io.openaev.engine.model.attackpattern.EsAttackPattern;
import io.openaev.engine.model.finding.EsFinding;
import io.openaev.engine.model.inject.EsInject;
import io.openaev.engine.model.injectexpectation.EsInjectExpectation;
import io.openaev.engine.model.scenario.EsScenario;
import io.openaev.engine.model.securitydomain.EsSecurityDomain;
import io.openaev.engine.model.securityplatform.EsSecurityPlatform;
import io.openaev.engine.model.simulation.EsSimulation;
import io.openaev.engine.model.snapshotobservation.EsAttackObservation;
import io.openaev.engine.model.snapshotobservation.EsVulnerabilityObservation;
import io.openaev.engine.model.tag.EsTag;
import io.openaev.engine.model.team.EsTeam;
import io.openaev.engine.model.vulnerableendpoint.EsVulnerableEndpoint;
import jakarta.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How each indexed document type is secured: the capability needed to read it, how it links to a
 * grant-managed resource, and which of its fields reference an asset. It only describes the
 * documents; the decision (does this reader hold the capability, the grant, the clearance) is taken
 * by the resolver from the source of truth, never here.
 *
 * <p>Every indexed type must be listed: a type missing from the registry is unreadable by any
 * restricted reader (fail closed), and a test checks that every {@code Handler} model is present.
 */
public final class EngineSecurityRegistry {

  /** A capability check: reading this type requires the capability of this pair. */
  public record ResourceAccess(ResourceType resourceType, Action action) {}

  /** A field linking a document to a grant-managed resource of this type. */
  public record GrantKeyField(String field, GRANT_RESOURCE_TYPE grantResourceType) {}

  /**
   * The security description of one indexed type.
   *
   * @param requiredAccess capabilities all required to read the type; empty for an open type
   * @param grantBypass the capability that makes grants irrelevant for this type (e.g. {@code
   *     ACCESS_ASSESSMENT} through SIMULATION READ); {@code null} when the type is not
   *     grant-managed
   * @param grantKeys the fields linking a document to a granted resource; empty when the type is
   *     not grant-managed
   * @param assetFields the fields holding the id of the asset(s) the document is about
   */
  public record EntitySecurity(
      List<ResourceAccess> requiredAccess,
      @Nullable ResourceAccess grantBypass,
      List<GrantKeyField> grantKeys,
      List<String> assetFields) {

    public boolean grantManaged() {
      return grantBypass != null;
    }
  }

  /**
   * Fields holding asset ids. Aggregations on them drop hidden assets, and returned documents have
   * hidden ids removed from them.
   */
  public static final Set<String> ASSET_REFERENCE_FIELDS =
      Set.of("base_assets_side", "base_asset_side", "base_endpoint_side", "vulnerable_endpoint_id");

  private static final ResourceAccess ASSESSMENT = access(ResourceType.SIMULATION, Action.READ);

  private static final List<GrantKeyField> ASSESSMENT_KEYS =
      List.of(
          new GrantKeyField("base_scenario_side", GRANT_RESOURCE_TYPE.SCENARIO),
          new GrantKeyField("base_simulation_side", GRANT_RESOURCE_TYPE.SIMULATION));

  private static final Map<String, EntitySecurity> ENTITIES = buildEntities();

  private EngineSecurityRegistry() {}

  private static Map<String, EntitySecurity> buildEntities() {
    Map<String, EntitySecurity> entities = new LinkedHashMap<>();
    // Inventory: the asset itself, and the security platforms stored in the same table.
    entities.put(
        index(EsAsset.class),
        open(List.of(access(ResourceType.ASSET, Action.READ)), List.of("base_id")));
    entities.put(
        index(EsSecurityPlatform.class),
        open(List.of(access(ResourceType.SECURITY_PLATFORM, Action.READ)), List.of("base_id")));
    entities.put(
        index(EsAssetGroup.class),
        open(List.of(access(ResourceType.ASSET_GROUP, Action.READ)), List.of()));
    // Open reference data (PermissionService RESOURCES_OPEN).
    entities.put(index(EsAttackPattern.class), open(List.of(), List.of()));
    entities.put(index(EsTag.class), open(List.of(), List.of()));
    entities.put(index(EsTeam.class), open(List.of(), List.of()));
    entities.put(index(EsSecurityDomain.class), open(List.of(), List.of()));
    // Assessment: scenarios, simulations and what their runs produce, managed by grants.
    entities.put(
        index(EsScenario.class),
        new EntitySecurity(
            List.of(),
            access(ResourceType.SCENARIO, Action.READ),
            List.of(new GrantKeyField("base_id", GRANT_RESOURCE_TYPE.SCENARIO)),
            List.of()));
    entities.put(
        index(EsSimulation.class),
        new EntitySecurity(
            List.of(),
            ASSESSMENT,
            List.of(
                new GrantKeyField("base_id", GRANT_RESOURCE_TYPE.SIMULATION),
                new GrantKeyField("base_scenario_side", GRANT_RESOURCE_TYPE.SCENARIO)),
            List.of()));
    entities.put(
        index(EsInject.class),
        new EntitySecurity(
            List.of(),
            ASSESSMENT,
            withAtomicTesting(new GrantKeyField("base_id", GRANT_RESOURCE_TYPE.ATOMIC_TESTING)),
            List.of()));
    entities.put(
        index(EsInjectExpectation.class),
        new EntitySecurity(
            List.of(),
            ASSESSMENT,
            withAtomicTesting(
                new GrantKeyField("base_inject_side", GRANT_RESOURCE_TYPE.ATOMIC_TESTING)),
            List.of("base_asset_side")));
    entities.put(
        index(EsFinding.class),
        new EntitySecurity(
            List.of(access(ResourceType.FINDING, Action.READ)),
            ASSESSMENT,
            withAtomicTesting(
                new GrantKeyField("base_inject_side", GRANT_RESOURCE_TYPE.ATOMIC_TESTING)),
            List.of("base_endpoint_side")));
    entities.put(
        index(EsVulnerableEndpoint.class),
        new EntitySecurity(
            List.of(
                access(ResourceType.ASSET, Action.READ), access(ResourceType.FINDING, Action.READ)),
            ASSESSMENT,
            ASSESSMENT_KEYS,
            List.of("vulnerable_endpoint_id")));
    // Snapshot export (dev feature BULK_SNAPSHOT_EXPORT); its capability is SEARCH-only.
    EntitySecurity observation =
        new EntitySecurity(
            List.of(access(ResourceType.SNAPSHOT_OBSERVATION, Action.SEARCH)),
            ASSESSMENT,
            ASSESSMENT_KEYS,
            List.of("base_asset_side"));
    entities.put(index(EsAttackObservation.class), observation);
    entities.put(index(EsVulnerabilityObservation.class), observation);
    return Map.copyOf(entities);
  }

  /** The security description of every indexed type, keyed by {@code base_entity}. */
  public static Map<String, EntitySecurity> entities() {
    return ENTITIES;
  }

  /** The security description of one type, or {@code null} when it is not registered. */
  @Nullable
  public static EntitySecurity get(String entity) {
    return ENTITIES.get(entity);
  }

  private static EntitySecurity open(List<ResourceAccess> access, List<String> assetFields) {
    return new EntitySecurity(access, null, List.of(), assetFields);
  }

  private static List<GrantKeyField> withAtomicTesting(GrantKeyField atomicTesting) {
    return List.of(ASSESSMENT_KEYS.get(0), ASSESSMENT_KEYS.get(1), atomicTesting);
  }

  private static ResourceAccess access(ResourceType resourceType, Action action) {
    return new ResourceAccess(resourceType, action);
  }

  private static String index(Class<? extends EsBase> model) {
    return model.getAnnotation(Indexable.class).index();
  }
}
