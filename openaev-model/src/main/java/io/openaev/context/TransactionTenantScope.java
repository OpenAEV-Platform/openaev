package io.openaev.context;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The tenant scope of a transaction: the one place that writes {@code app.current_tenants}, and the
 * one tenant that write pins on the current transaction, for code that cannot run a query while the
 * session flushes (the entity events of a background write).
 *
 * <p>Two classes write through it, the transaction aspect and {@link TenantScopedTransaction};
 * {@code ScopeChannelSourceTripwireTest} pins that list. Writing and recording in one method means
 * every scope a transaction runs under is the scope it pins.
 */
public final class TransactionTenantScope {

  private TransactionTenantScope() {}

  /** Writes {@code scope} into the transaction-local tenant setting, then pins its tenant. */
  public static void write(EntityManager entityManager, String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setFlushMode(FlushModeType.COMMIT)
        .setParameter("scope", scope)
        .getSingleResult();
    Pin.record(scope);
  }

  /**
   * The one tenant the scope last written on the current transaction holds. Empty outside a
   * transaction, before a scope is written on it, or when that scope holds no tenant or several.
   * Read without a query.
   */
  public static Optional<String> pinnedTenant() {
    return TransactionSynchronizationManager.getResource(Pin.class) instanceof Pin pin
        ? pin.tenant
        : Optional.empty();
  }

  /**
   * Whether the scope last written on the current transaction holds more than one tenant. False
   * outside a transaction and before a scope is written on it. Read without a query.
   */
  public static boolean holdsSeveralTenants() {
    return TransactionSynchronizationManager.getResource(Pin.class) instanceof Pin pin
        && pin.tenantCount > 1;
  }

  /**
   * Whether the scope last written on the current transaction holds no tenant, the scope of a
   * request that names none. False outside a transaction and before a scope is written on it. Read
   * without a query.
   */
  public static boolean holdsNoTenant() {
    return TransactionSynchronizationManager.getResource(Pin.class) instanceof Pin pin
        && pin.tenantCount == 0;
  }

  private static final class Pin implements TransactionSynchronization {

    private Optional<String> tenant;
    private int tenantCount;

    private Pin(Set<String> tenants) {
      set(tenants);
    }

    private void set(Set<String> tenants) {
      this.tenant = tenants.size() == 1 ? tenants.stream().findFirst() : Optional.empty();
      this.tenantCount = tenants.size();
    }

    /**
     * Binds the pin to the current transaction, and to it only: a synchronization also runs around
     * a scope with no transaction ({@code NOT_SUPPORTED}, {@code SUPPORTS}), where the setting ends
     * with its own statement, so a pin recorded there would name a tenant no later statement runs
     * under.
     */
    private static void record(String scope) {
      if (!TransactionSynchronizationManager.isActualTransactionActive()) {
        return;
      }
      Set<String> tenants = tenantsOf(scope);
      if (TransactionSynchronizationManager.getResource(Pin.class) instanceof Pin bound) {
        bound.set(tenants);
        return;
      }
      Pin pin = new Pin(tenants);
      TransactionSynchronizationManager.bindResource(Pin.class, pin);
      TransactionSynchronizationManager.registerSynchronization(pin);
    }

    private static Set<String> tenantsOf(String scope) {
      return scope.isEmpty()
          ? Set.of()
          : Arrays.stream(scope.split(",")).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void suspend() {
      TransactionSynchronizationManager.unbindResourceIfPossible(Pin.class);
    }

    @Override
    public void resume() {
      TransactionSynchronizationManager.unbindResourceIfPossible(Pin.class);
      TransactionSynchronizationManager.bindResource(Pin.class, this);
    }

    @Override
    public void afterCompletion(int status) {
      TransactionSynchronizationManager.unbindResourceIfPossible(Pin.class);
    }
  }
}
