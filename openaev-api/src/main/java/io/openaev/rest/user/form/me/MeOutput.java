package io.openaev.rest.user.form.me;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.openaev.database.model.Capability;
import io.openaev.database.model.User;
import java.util.Map;
import java.util.Set;

/**
 * Output DTO for the {@code /api/me} endpoint.
 *
 * <p>Wraps the {@link User} entity (serialized as-is via {@link JsonUnwrapped}) and adds
 * tenant-scoped fields that require a {@code tenantId} to compute and therefore cannot be
 * serialized directly from the entity (Jackson can't call parameterized getters).
 */
public record MeOutput(
    @JsonUnwrapped User user,
    @JsonProperty("user_capabilities") Set<Capability> capabilities,
    @JsonProperty("user_grants") Map<String, String> grants,
    @JsonProperty("user_is_admin_or_bypass") boolean adminOrBypass) {

  /** Factory: builds a MeOutput by resolving tenant-scoped fields from the User entity. */
  public static MeOutput from(User user, String tenantId) {
    return new MeOutput(
        user,
        tenantId != null ? user.getCapabilities(tenantId) : Set.of(),
        tenantId != null ? user.getGrants(tenantId) : Map.of(),
        tenantId != null && user.isAdminOrBypass(tenantId));
  }
}
