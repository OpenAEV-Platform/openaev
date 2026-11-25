package io.openaev.service.account;

import io.openaev.database.model.Capability;
import java.util.Set;

/**
 * A well-known role and the group that grants it. When an {@link AbstractPrivilegeService} ensures
 * them in a tenant, the ids are not stored as is: each one is derived per tenant through {@link
 * AbstractPrivilegeService#getUUIDFromName}.
 */
public record WellKnownAccess(
    String roleId,
    String roleName,
    String roleDescription,
    Set<Capability> capabilities,
    String groupId,
    String groupName,
    String groupDescription) {}
