package io.openaev.service;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Runs a copy (duplicate, relaunch, scenario launch) so that it keeps what the requester cannot
 * see.
 *
 * <p>The marking filter of a request hides the rows the user is not cleared for, the targets on a
 * hidden asset among them ({@code injects_assets} is filtered through its asset). A copy built from
 * that view silently drops those targets, and a relaunch, which deletes the original, loses them
 * for everyone. A faithful copy reads the origin with the clearance of the system identity: every
 * marking of the request's tenants, never more tenants. What the user may do is unchanged, still
 * checked on the parent by {@code @AccessControl}; the dispatch then scopes the execution to the
 * launcher's clearance, as on a first launch.
 *
 * <p>The copy joins the request's transaction ({@link
 * TenantScopedTransaction#withSystemClearance}), which gets its clearance back afterwards. Two
 * rules keep it from becoming a way to read hidden data:
 *
 * <ul>
 *   <li>Only the id of the copy comes out. The response is built afterwards, under the user's
 *       clearance, from what the caller reloads.
 *   <li>The persistence context is cleared on both sides of the copy: it reloads the origin by id,
 *       and the caller does not reuse an entity loaded before it (now detached).
 * </ul>
 *
 * <p>This is the one HTTP-side door to the system clearance; its callers are an allow-list ({@code
 * FaithfulCopyCallersArchTest}).
 */
@Component
@RequiredArgsConstructor
public class FaithfulCopyTransaction {

  /** A copy that returns the id of what it created. */
  @FunctionalInterface
  public interface Copy<E extends Exception> {
    String run() throws E;
  }

  private final TenantScopedTransaction tenantTx;

  /**
   * Runs {@code copy} in the current transaction with every marking of the tenants of {@code ctx},
   * and returns the id of the copy.
   *
   * @param ctx the request's tenant scope. Only an explicit one ({@link TxCtx.Restricted}) has a
   *     clearance to widen to; without one (a caller with no tenant membership), the copy runs
   *     under the request's own clearance, as it did before
   */
  public <E extends Exception> String execute(TxCtx ctx, Copy<E> copy) throws E {
    if (!(ctx instanceof TxCtx.Restricted)) {
      return copy.run();
    }
    try {
      return tenantTx.withSystemClearance(
          ctx,
          () -> {
            try {
              return copy.run();
            } catch (RuntimeException e) {
              throw e;
            } catch (Exception e) {
              throw new CheckedCopyFailure(e);
            }
          });
    } catch (CheckedCopyFailure failure) {
      @SuppressWarnings("unchecked")
      E cause = (E) failure.getCause();
      throw cause;
    }
  }

  /** Carries a checked exception through the {@link java.util.function.Supplier} of the copy. */
  private static final class CheckedCopyFailure extends RuntimeException {
    CheckedCopyFailure(Exception cause) {
      super(cause);
    }
  }
}
