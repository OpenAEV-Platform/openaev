package io.openaev.utils;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.Channel;
import io.openaev.database.model.Document;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Group;
import io.openaev.database.model.Inject;
import io.openaev.database.model.KillChainPhase;
import io.openaev.database.model.Objective;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Payload;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Role;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceManagerUtils {

  /** Resource types classified as administration — auth events, RBAC changes, platform settings. */
  public static final Set<ResourceType> ADMINISTRATION_RESOURCE_TYPES =
      buildAdministrationResourceTypes();

  public static final Map<ResourceType, Class<?>> ENTITY_CLASS_MAP = buildEntityClassMap();

  /** Reverse lookup: JPA entity class → ResourceType (built from {@link #ENTITY_CLASS_MAP}). */
  public static final Map<Class<?>, ResourceType> REVERSE_ENTITY_CLASS_MAP =
      buildReverseEntityClassMap();

  public final EntityManagerUtils entityManagerUtils;

  public static Class<?> getClassByResource(ResourceType resourceType) {
    return ENTITY_CLASS_MAP.get(resourceType);
  }

  public static ResourceType getResourceByClass(Class<?> entityClass) {
    return REVERSE_ENTITY_CLASS_MAP.get(entityClass);
  }

  public <T> T findResourceEntity(ResourceType resourceType, Object entityId) {
    try {
      Class<?> entityClass = getClassByResource(resourceType);

      if (entityClass == null) {
        return null;
      }
      return entityManagerUtils.findEntity((Class<T>) entityClass, entityId);
    } catch (Exception e) {
      log.debug(
          "[ResourceManagerUtils] Failed to find entity {}/{}: {}",
          resourceType,
          entityId,
          e.getMessage());
    }
    return null;
  }

  public JsonNode snapshotResourceEntity(ResourceType resourceType, Object entityId) {
    try {
      Class<?> entityClass = getClassByResource(resourceType);

      if (entityClass == null) {
        return null;
      }
      return entityManagerUtils.snapshotEntity((Class<?>) entityClass, entityId);
    } catch (Exception e) {
      log.debug(
          "[ResourceManagerUtils] Failed to snapshot resource entity {}/{}: {}",
          resourceType,
          entityId,
          e.getMessage());
    }
    return null;
  }

  public static String extractNameFromSnapshot(JsonNode snapshot) {
    return EntityManagerUtils.extractNameFromSnapshot(snapshot);
  }

  /**
   * Builds the ResourceType → JPA entity class mapping. Only includes entity types that have a
   * direct 1:1 JPA entity. This map is used for {@code EntityManager.find()} pre-fetch.
   */
  private static Map<ResourceType, Class<?>> buildEntityClassMap() {
    return Map.ofEntries(
        Map.entry(ResourceType.SCENARIO, Scenario.class),
        Map.entry(ResourceType.SIMULATION, Exercise.class),
        Map.entry(ResourceType.USER, User.class),
        Map.entry(ResourceType.TEAM, Team.class),
        Map.entry(ResourceType.INJECT, Inject.class),
        Map.entry(ResourceType.DOCUMENT, Document.class),
        Map.entry(ResourceType.TAG, Tag.class),
        Map.entry(ResourceType.CHANNEL, Channel.class),
        Map.entry(ResourceType.CHALLENGE, Challenge.class),
        Map.entry(ResourceType.PAYLOAD, Payload.class),
        Map.entry(ResourceType.ASSET_GROUP, AssetGroup.class),
        Map.entry(ResourceType.OBJECTIVE, Objective.class),
        Map.entry(ResourceType.ORGANIZATION, Organization.class),
        Map.entry(ResourceType.KILL_CHAIN_PHASE, KillChainPhase.class),
        Map.entry(ResourceType.ATTACK_PATTERN, AttackPattern.class),
        Map.entry(ResourceType.USER_GROUP, Group.class),
        Map.entry(ResourceType.GROUP_ROLE, Role.class));
  }

  /** Builds the reverse mapping: JPA entity class → ResourceType. */
  private static Map<Class<?>, ResourceType> buildReverseEntityClassMap() {
    Map<Class<?>, ResourceType> reverse = new HashMap<>();
    ENTITY_CLASS_MAP.forEach((resourceType, clazz) -> reverse.put(clazz, resourceType));
    return Map.copyOf(reverse);
  }

  private static Set<ResourceType> buildAdministrationResourceTypes() {
    return Set.of(
        ResourceType.USER,
        ResourceType.USER_GROUP,
        ResourceType.GROUP_ROLE,
        ResourceType.PLATFORM_SETTING,
        ResourceType.TENANT,
        ResourceType.ORGANIZATION);
  }
}
