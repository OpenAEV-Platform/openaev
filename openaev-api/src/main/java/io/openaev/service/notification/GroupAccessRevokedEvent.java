package io.openaev.service.notification;

import io.openaev.database.model.Grant;
import java.util.List;
import java.util.Set;

/**
 * Published synchronously by {@code TenantGroupService} just BEFORE a change that can take grants
 * away from users (a grant removed, members removed from a group, a group deleted), while the
 * grants still exist, so a listener can read who reads the granted objects before the change.
 *
 * @param tenantId the tenant of the group
 * @param targets the granted objects the users may lose
 * @param userIds the users who may lose them
 */
public record GroupAccessRevokedEvent(String tenantId, List<Target> targets, Set<String> userIds) {

  /** One granted object. */
  public record Target(Grant.GRANT_RESOURCE_TYPE resourceType, String resourceId) {}

  public GroupAccessRevokedEvent {
    targets = List.copyOf(targets);
    userIds = Set.copyOf(userIds);
  }

  public static GroupAccessRevokedEvent of(
      String tenantId, List<Grant> grants, Set<String> userIds) {
    return new GroupAccessRevokedEvent(
        tenantId,
        grants.stream()
            .filter(grant -> grant.getResourceId() != null)
            .map(grant -> new Target(grant.getGrantResourceType(), grant.getResourceId()))
            .distinct()
            .toList(),
        userIds);
  }
}
