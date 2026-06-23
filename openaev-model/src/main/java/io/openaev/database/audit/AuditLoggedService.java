package io.openaev.database.audit;

import java.util.Map;

/**
 * Interface for services that manage audit logging suppression based on entity significance.
 *
 * <p>Provides a default method that compares a previously captured state snapshot against the
 * current entity state and disables audit logging via {@link AuditLogContext} when no significant
 * change is detected.
 */
public interface AuditLoggedService {

  /**
   * Suppresses audit logging if the entity has not changed significantly between the two states.
   *
   * @param before state snapshot captured before mutation via {@link
   *     AuditStateCapturable#significantState}
   * @param after state snapshot captured after mutation via {@link
   *     AuditStateCapturable#significantState}
   */
  default void suppressAuditIfUnchanged(Map<String, Object> before, Map<String, Object> after) {
    if (before.equals(after)) {
      AuditLogContext.setEnabled(false);
    }
  }
}
