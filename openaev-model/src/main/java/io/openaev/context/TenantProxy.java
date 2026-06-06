package io.openaev.context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * Creates JDK dynamic proxies that enforce tenant isolation on any Spring Data repository.
 *
 * <h3>Usage pattern</h3>
 *
 * <p>Each repository exposes a {@code forTenant(OperationState)} factory method backed by this
 * utility. The caller <em>cannot</em> invoke any repository method without providing an {@link
 * ExecState} — enforced at compile time:
 *
 * <pre>{@code
 * // compile error — DocumentRepository no longer exposes JPA methods directly
 * documentRepository.findById(id);
 *
 * // correct — tenant context is set automatically for every call
 * documentRepository.forTenant(state).findById(id);
 *
 * // also correct in parallel — each call scopes its own thread
 * ids.parallelStream()
 *    .map(id -> documentRepository.forTenant(state).findById(id))
 *    .toList();
 * }</pre>
 *
 * <h3>How it works</h3>
 *
 * <p>The proxy intercepts every method call and wraps it in a narrow {@link StateExecutionContext}
 * window: set the tenant list before the call, restore the previous context after (safe to nest).
 * The {@link io.openaev.config.TenantStatementInspector} reads this context and rewrites the SQL.
 *
 * <h3>Creating a new tenant-aware repository</h3>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class ExerciseRepository {
 *     private final ExerciseJpaRepository internal;
 *
 *     public ExerciseJpaRepository forTenant(OperationState op) {
 *         return TenantProxy.of(internal, ExerciseJpaRepository.class, op);
 *     }
 * }
 * }</pre>
 */
public final class TenantProxy {

  private TenantProxy() {}

  /**
   * Creates a tenant-scoped proxy for the given repository delegate.
   *
   * @param delegate the Spring Data repository bean to delegate actual calls to
   * @param iface the repository interface to proxy (must be an interface)
   * @param op the tenant scope for all calls made through the returned proxy
   * @param <T> the repository interface type
   * @return a proxy that sets {@link StateExecutionContext} around every method call
   */
  @SuppressWarnings("unchecked")
  public static <T> T of(T delegate, Class<T> iface, ExecState op) {
    return (T)
        Proxy.newProxyInstance(
            iface.getClassLoader(),
            new Class<?>[] {iface},
            (proxy, method, args) -> {
              // Save any previously active context (safe to nest — e.g. service calling another
              // service)
              ExecState previous = StateExecutionContext.get();
              try {
                StateExecutionContext.set(op);
                return method.invoke(delegate, args);
              } catch (InvocationTargetException e) {
                // Unwrap the reflection wrapper so callers see the original exception
                throw e.getCause();
              } finally {
                if (previous == null) {
                  StateExecutionContext.clear();
                } else {
                  StateExecutionContext.set(previous);
                }
              }
            });
  }
}
