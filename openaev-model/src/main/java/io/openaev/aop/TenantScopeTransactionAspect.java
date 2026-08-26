package io.openaev.aop;

import io.openaev.context.MarkingCtx;
import io.openaev.context.MarkingScopeSupplier;
import io.openaev.context.TxCtx;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Writes the tenant scope into the transaction-local {@code app.current_tenants} setting at the
 * start of each {@code @Transactional} method, so {@code can_access_tenant} filters that
 * transaction against it.
 *
 * <p>The scope is taken from a {@link TxCtx} method parameter, never from an ambient thread-local:
 * for an HTTP request the binding resolves it and passes it as an argument; background work passes
 * it explicitly. This is the deliberate v2 correction over v1, where the scope lived on the thread
 * and was lost off it. A {@code @Transactional} method without a {@link TxCtx} parameter is not
 * tenant-scoped, so the setting is left untouched and the aspect stays inert until a method opts
 * in.
 *
 * <p>{@link io.openaev.context.TxCtx.Missing} writes an empty value, which denies every row
 * (fail-closed). The value is transaction-local ({@code set_config(..., true)}): it clears itself
 * when the transaction ends and cannot leak into the next one through a reused connection.
 *
 * <p>Correctness rests on this advice running <em>inside</em> the transaction, i.e. after it has
 * begun: a transaction-local setting written before BEGIN would land in a throwaway auto-commit and
 * never reach the method. The integration test pins this down for the propagations the application
 * actually uses (REQUIRED, REQUIRES_NEW, read-only).
 *
 * <p>Both scope dimensions are written here, together: {@code app.current_tenants} from the {@link
 * TxCtx} argument, and {@code app.current_markings} derived from the caller through {@link
 * MarkingScopeSupplier}. Tenant is passed because a caller legitimately chooses which of their
 * tenants to act in; clearance is derived because nobody chooses their own. Writing both in one
 * place is what keeps "a transaction's scope is set once" a single rule rather than two that have
 * to agree.
 *
 * <p>⚠️ A {@code @Transactional} method with no {@link TxCtx} parameter writes <b>neither</b>
 * setting. For marking that means the transaction sees only <i>unmarked</i> rows of any
 * marking-active table — a partial, silent narrowing rather than an obvious empty result. Harmless
 * while no table is marking-active; it is the blast radius that activating one has to account for.
 *
 * <p>Within one transaction the scope is set once. A nested {@code @Transactional} method that
 * would <em>change</em> an already-set scope is refused (a programming error), while one that
 * repeats the same set of tenants is tolerated. A nested method that needs a different scope must
 * open a {@code REQUIRES_NEW} transaction.
 */
@Aspect
@Component
@RequiredArgsConstructor
// Innermost of the chain: it runs as a @Before once the transaction advisor has opened the tx.
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantScopeTransactionAspect {

  private final EntityManager entityManager;

  /**
   * Optional so the aspect keeps working in contexts that do not wire the API layer (model tests,
   * and any slice without it). Absent means no marking scope is written at all, which leaves the
   * setting empty — see the class javadoc for what that implies once a table is marking-active.
   */
  private final ObjectProvider<MarkingScopeSupplier> markingScopeSupplier;

  @Before(
      "@annotation(org.springframework.transaction.annotation.Transactional) || "
          + "@annotation(jakarta.transaction.Transactional)")
  public void applyScope(JoinPoint joinPoint) {
    TxCtx ctx = findTxCtx(joinPoint.getArgs());
    if (ctx == null) {
      return;
    }
    String desired = ctx.toGuc();
    String current = currentScope();
    if (!current.isEmpty()) {
      // A scope is the set of tenants it grants, not its textual order, so compare as sets.
      if (tenants(current).equals(tenants(desired))) {
        return;
      }
      throw new IllegalStateException(
          ("Tenant scope '%s' is already set for this transaction; method '%s' tried to change it to"
                  + " '%s'. A nested @Transactional method must not redefine the scope; pass the same"
                  + " TxCtx or open a REQUIRES_NEW transaction.")
              .formatted(current, joinPoint.getSignature().toShortString(), desired));
    }
    setScope(desired);
    setMarkingScope(markingScopeFor(ctx));
  }

  /**
   * Derives the caller's clearance. Deliberately fail-closed on both the absent-supplier and the
   * absent-principal paths: an empty setting still admits unmarked rows, so the failure mode is a
   * narrower result set, never a wider one.
   */
  private String markingScopeFor(TxCtx ctx) {
    MarkingScopeSupplier supplier = markingScopeSupplier.getIfAvailable();
    if (supplier == null) {
      return MarkingCtx.none().toGuc();
    }
    return supplier.clearanceFor(ctx).toGuc();
  }

  private static Set<String> tenants(String guc) {
    return guc.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(guc.split(",")));
  }

  private String currentScope() {
    return (String)
        entityManager
            .createNativeQuery("SELECT coalesce(current_setting('app.current_tenants', true), '')")
            .getSingleResult();
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  private void setMarkingScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_markings', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  private static TxCtx findTxCtx(Object[] args) {
    if (args == null) {
      return null;
    }
    for (Object arg : args) {
      if (arg instanceof TxCtx ctx) {
        return ctx;
      }
    }
    return null;
  }
}
