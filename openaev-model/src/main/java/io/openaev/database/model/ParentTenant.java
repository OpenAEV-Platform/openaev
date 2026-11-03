package io.openaev.database.model;

/**
 * Attribution of a chaining row: it belongs to its parent's tenant. Taken from the parent row
 * rather than from the tenant of the request or job thread, which background and scheduled paths do
 * not always carry.
 *
 * <p>No {@code TenantBaseListener} on these entities on purpose: its thread-local fallback would
 * hide a write path that has no parent to take a tenant from. Such a write is refused instead.
 */
final class ParentTenant {

  private ParentTenant() {}

  /**
   * The tenant a new row must carry.
   *
   * @param row the row being persisted, named in the error
   * @param current the tenant already set on the row, if any
   * @param parent the tenant of the row's parent, or {@code null} when it has no parent to read
   * @return the parent's tenant, or the one already set when there is no parent
   * @throws IllegalStateException when there is neither, or when they differ
   */
  static Tenant inherit(Object row, Tenant current, Tenant parent) {
    String rowType = row.getClass().getSimpleName();
    if (parent == null) {
      if (current == null) {
        throw new IllegalStateException(
            "unattributed tenant write: " + rowType + " has no parent to take a tenant from");
      }
      return current;
    }
    if (current != null && !current.getId().equals(parent.getId())) {
      throw new IllegalStateException(
          "cross-tenant write: " + rowType + " carries a tenant other than its parent's");
    }
    return parent;
  }
}
