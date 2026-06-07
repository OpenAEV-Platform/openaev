package io.openaev.context;

import java.util.function.Supplier;

/**
 * Narrow-window ThreadLocal for tenant execution context.
 *
 * <h3>Two modes of use</h3>
 *
 * <p><b>1. Request-scoped (HTTP handlers)</b> — set once by {@link
 * io.openaev.config.TenantInterceptor} at the start of the request, cleared at the end. Services
 * and repositories need no changes.
 *
 * <pre>{@code
 * // TenantInterceptor.preHandle()
 * TenantExecutionContext.set(OperationState.of(tenantId));
 *
 * // TenantInterceptor.afterCompletion()
 * TenantExecutionContext.clear();
 * }</pre>
 *
 * <p><b>2. Explicit narrow window (parallel streams / jobs)</b> — set and cleared within a single
 * {@link #run} call using try/finally. Safe for parallel operations because each task explicitly
 * declares its own tenant scope. The previous context (if any) is saved and restored.
 *
 * <pre>{@code
 * // Parallel within a request
 * ids.parallelStream()
 *    .map(id -> TenantExecutionContext.run(op, () -> repository.findById(id)))
 *    .toList();
 *
 * // Background job iterating tenants
 * tenants.forEach(t ->
 *     TenantExecutionContext.run(OperationState.of(t.getId()), () -> processForTenant(t))
 * );
 * }</pre>
 *
 * <p>The {@link io.openaev.config.TenantStatementInspector} reads this value before each SQL
 * statement and automatically adds the tenant filter.
 */
public final class StateExecutionContext {

  private static final ThreadLocal<ExecState> CURRENT_STATE = new ThreadLocal<>();

  private StateExecutionContext() {}

  // ---------------------------------------------------------------------------
  // Request-scoped API (called by TenantInterceptor for HTTP requests)
  // ---------------------------------------------------------------------------

  /**
   * Sets the tenant context for the current thread. Must be paired with {@link #clear()} in a
   * finally block. Prefer {@link #run} for narrow-window scoping — use this only from the
   * interceptor or from job schedulers that manage their own lifecycle.
   */
  public static void set(ExecState state) {
    CURRENT_STATE.set(state);
  }

  /**
   * Clears the tenant context for the current thread. Called by {@link
   * io.openaev.config.TenantInterceptor} in {@code afterCompletion}.
   */
  public static void clear() {
    CURRENT_STATE.remove();
  }

  // ---------------------------------------------------------------------------
  // Narrow-window API (parallel streams / jobs with child threads)
  // ---------------------------------------------------------------------------

  /**
   * Executes the given supplier within a narrow tenant scope. Saves and restores any previously
   * active tenant context (safe to nest, e.g. parallel call inside an HTTP request).
   */
  public static <T> T run(ExecState state, Supplier<T> task) {
    ExecState previous = CURRENT_STATE.get();
    try {
      CURRENT_STATE.set(state);
      return task.get();
    } finally {
      if (previous == null) {
        CURRENT_STATE.remove();
      } else {
        CURRENT_STATE.set(previous);
      }
    }
  }

  /**
   * Executes the given runnable within a narrow tenant scope. Saves and restores any previously
   * active tenant context.
   */
  public static void run(ExecState state, Runnable task) {
    ExecState previous = CURRENT_STATE.get();
    try {
      CURRENT_STATE.set(state);
      task.run();
    } finally {
      if (previous == null) {
        CURRENT_STATE.remove();
      } else {
        CURRENT_STATE.set(previous);
      }
    }
  }

  /**
   * Returns the current effective ExecState, or {@code null} if no context is active. Called by
   * {@link io.openaev.config.TenantStatementInspector} before each SQL statement.
   */
  public static ExecState get() {
    return CURRENT_STATE.get();
  }
}
