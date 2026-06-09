package io.openaev.context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

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
             //  ExecState previous = StateExecutionContext.get();
              try {
                // StateExecutionContext.set(op);
                return method.invoke(delegate, args);
              } catch (InvocationTargetException e) {
                // Unwrap the reflection wrapper so callers see the original exception
                throw e.getCause();
              } finally {
                // if (previous == null) {
                //   // StateExecutionContext.clear();
                // } else {
                //   // StateExecutionContext.set(previous);
                // }
              }
            });
  }
}
