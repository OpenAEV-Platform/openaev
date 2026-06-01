package io.openaev.utils;

import io.openaev.database.model.Tenant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Utility for generating deterministic UUIDs (UUID v3) from string inputs. Produces stable,
 * reproducible identifiers suitable for deriving tenant-scoped entity IDs, relationship IDs, or any
 * case where a predictable UUID is needed from known inputs.
 */
public final class DeterministicIdUtils {

  private DeterministicIdUtils() {}

  /**
   * Resolves the actual database ID for a connector (injector, executor, collector) given its
   * static/constant ID and the tenant context.
   *
   * <p>For the default tenant, the original static ID is returned as-is (backward compatibility).
   * For any other tenant, a deterministic UUID is derived from the static ID + tenant ID.
   *
   * @param staticId the constant ID defined in integration code (e.g. EMAIL_INJECTOR_ID)
   * @param tenantId the tenant identifier
   * @return the actual ID to use in database operations
   */
  public static String resolveConnectorId(String staticId, String tenantId) {
    return Tenant.DEFAULT_TENANT_UUID.equals(tenantId) ? staticId : derive(staticId, tenantId);
  }

  /**
   * Derives a deterministic UUID by joining the given parts with {@code ":"} separator.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code derive("base-id", "tenant-id")} — tenant-scoped entity ID
   *   <li>{@code derive("namespace", "tenant-id")} — connector ID
   *   <li>{@code derive("source-id", "target-id")} — relationship ID
   * </ul>
   *
   * @param parts one or more string components to combine
   * @return a deterministic UUID string
   * @throws IllegalArgumentException if no parts are provided
   */
  public static String derive(String... parts) {
    if (parts == null || parts.length == 0) {
      throw new IllegalArgumentException("At least one part is required");
    }
    String input = String.join(":", parts);
    return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8)).toString();
  }
}
