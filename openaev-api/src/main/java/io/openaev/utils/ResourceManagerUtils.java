package io.openaev.utils;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceManagerUtils {

    /** Resource types classified as administration — auth events, RBAC changes, platform settings. */
    public static final Set<ResourceType> ADMINISTRATION_RESOURCE_TYPES = buildAdministrationResourceTypes();

    public static final Map<ResourceType, Class<?>> ENTITY_CLASS_MAP = buildEntityClassMap();

    /** Reverse lookup: JPA entity class → ResourceType (built from {@link #ENTITY_CLASS_MAP}). */
    public static final Map<Class<?>, ResourceType> REVERSE_ENTITY_CLASS_MAP = buildReverseEntityClassMap();

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
            log.debug("[ResourceManagerUtils] Failed to find entity {}/{}: {}", resourceType, entityId, e.getMessage());
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
            log.debug("[ResourceManagerUtils] Failed to snapshot resource entity {}/{}: {}", resourceType, entityId, e.getMessage());
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
        try {
            return Map.ofEntries(
                    Map.entry(ResourceType.SCENARIO, Class.forName("io.openaev.database.model.Scenario")),
                    Map.entry(ResourceType.SIMULATION, Class.forName("io.openaev.database.model.Exercise")),
                    Map.entry(ResourceType.USER, Class.forName("io.openaev.database.model.User")),
                    Map.entry(ResourceType.TEAM, Class.forName("io.openaev.database.model.Team")),
                    Map.entry(ResourceType.INJECT, Class.forName("io.openaev.database.model.Inject")),
                    Map.entry(ResourceType.DOCUMENT, Class.forName("io.openaev.database.model.Document")),
                    Map.entry(ResourceType.TAG, Class.forName("io.openaev.database.model.Tag")),
                    Map.entry(ResourceType.CHANNEL, Class.forName("io.openaev.database.model.Channel")),
                    Map.entry(ResourceType.CHALLENGE, Class.forName("io.openaev.database.model.Challenge")),
                    Map.entry(ResourceType.PAYLOAD, Class.forName("io.openaev.database.model.Payload")),
                    Map.entry(
                            ResourceType.ASSET_GROUP, Class.forName("io.openaev.database.model.AssetGroup")),
                    Map.entry(ResourceType.OBJECTIVE, Class.forName("io.openaev.database.model.Objective")),
                    Map.entry(
                            ResourceType.ORGANIZATION, Class.forName("io.openaev.database.model.Organization")),
                    Map.entry(
                            ResourceType.KILL_CHAIN_PHASE,
                            Class.forName("io.openaev.database.model.KillChainPhase")),
                    Map.entry(
                            ResourceType.ATTACK_PATTERN,
                            Class.forName("io.openaev.database.model.AttackPattern")),
                    Map.entry(ResourceType.USER_GROUP, Class.forName("io.openaev.database.model.Group")),
                    Map.entry(ResourceType.GROUP_ROLE, Class.forName("io.openaev.database.model.Role")));
        } catch (ClassNotFoundException e) {
            log.error("[EntityManagerUtils] Failed to build entity class map: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    /** Builds the reverse mapping: JPA entity class → ResourceType. */
    private static Map<Class<?>, ResourceType> buildReverseEntityClassMap() {
        Map<Class<?>, ResourceType> reverse = new java.util.HashMap<>();
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
