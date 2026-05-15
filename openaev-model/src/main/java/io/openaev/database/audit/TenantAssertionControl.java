package io.openaev.database.audit;

/**
 * Thread-local flag to temporarily suppress the tenant immutability assertion in {@link
 * TenantBaseListener}. Used by infrastructure code (e.g. built-in connector registration) that
 * legitimately switches tenant context while operating on cross-tenant data.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * TenantAssertionControl.suppress();
 * try {
 *     // code that updates entities across tenants
 * } finally {
 *     TenantAssertionControl.restore();
 * }
 * }</pre>
 */
public final class TenantAssertionControl {

  private static final ThreadLocal<Boolean> SUPPRESSED =
      ThreadLocal.withInitial(() -> Boolean.FALSE);

  private TenantAssertionControl() {}

  /** Suppresses the tenant assertion for the current thread. */
  public static void suppress() {
    SUPPRESSED.set(Boolean.TRUE);
  }

  /** Restores the tenant assertion for the current thread. */
  public static void restore() {
    SUPPRESSED.remove();
  }

  /** Returns true if the assertion is currently suppressed. */
  public static boolean isSuppressed() {
    return SUPPRESSED.get();
  }
}
