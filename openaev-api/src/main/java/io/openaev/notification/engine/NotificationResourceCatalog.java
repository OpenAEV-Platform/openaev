package io.openaev.notification.engine;

import io.openaev.database.model.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Curated catalog of resource types that notification triggers can watch.
 *
 * <p>Each entry maps a {@link ResourceType} exposed to users to the JPA entity class used for
 * filter matching (the same class the frontend filter builder resolves via {@code
 * /api/schemas/&#123;className&#125;}).
 */
@Getter
@RequiredArgsConstructor
public enum NotificationResourceCatalog {
  SCENARIO(ResourceType.SCENARIO, Scenario.class),
  SIMULATION(ResourceType.SIMULATION, Exercise.class),
  INJECT(ResourceType.INJECT, Inject.class),
  FINDING(ResourceType.FINDING, Finding.class),
  ASSET(ResourceType.ASSET, Endpoint.class),
  ASSET_GROUP(ResourceType.ASSET_GROUP, AssetGroup.class),
  TEAM(ResourceType.TEAM, Team.class),
  PLAYER(ResourceType.PLAYER, User.class),
  PAYLOAD(ResourceType.PAYLOAD, Payload.class),
  VULNERABILITY(ResourceType.VULNERABILITY, Vulnerability.class),
  SECURITY_PLATFORM(ResourceType.SECURITY_PLATFORM, SecurityPlatform.class),
  DOCUMENT(ResourceType.DOCUMENT, Document.class),
  CHALLENGE(ResourceType.CHALLENGE, Challenge.class);

  private final ResourceType resourceType;
  private final Class<? extends Base> entityClass;

  private static final Map<ResourceType, NotificationResourceCatalog> BY_RESOURCE_TYPE =
      Arrays.stream(values())
          .collect(
              LinkedHashMap::new, (map, entry) -> map.put(entry.resourceType, entry), Map::putAll);

  public static Optional<NotificationResourceCatalog> fromResourceType(ResourceType resourceType) {
    return Optional.ofNullable(BY_RESOURCE_TYPE.get(resourceType));
  }

  /**
   * Resolves the catalog entry matching a concrete entity instance class, if any. Uses {@code
   * isAssignableFrom} so subclasses (e.g. {@code Endpoint} extending {@code Asset}) resolve too.
   */
  public static Optional<NotificationResourceCatalog> fromEntity(Base instance) {
    Class<?> instanceClass = instance.getClass();
    return Arrays.stream(values())
        .filter(entry -> entry.entityClass.isAssignableFrom(instanceClass))
        .findFirst();
  }
}
