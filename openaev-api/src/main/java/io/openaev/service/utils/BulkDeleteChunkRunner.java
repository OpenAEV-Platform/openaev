package io.openaev.service.utils;

import io.openaev.context.TxCtx;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional trampoline for {@link BulkDeleteExecutor}: runs one unit of bulk-deletion work in
 * its own transaction when the caller is not already transactional (REQUIRED propagation). The bulk
 * API endpoints are deliberately non-transactional, so each chunk commits independently and row
 * locks on hot tables (e.g. {@code exercises}, concurrently updated by inject expectation results)
 * are held only briefly.
 *
 * <p>Going through this bean (a Spring proxy) also fires {@code HibernateFilterTransactionAspect},
 * which enables the Hibernate tenant filter on the chunk's session - a plain {@code
 * TransactionTemplate} would silently skip tenant scoping.
 *
 * <p>Other chunked massive operations (e.g. {@link
 * io.openaev.api.expectations.ExpectationsDriftService} realignment) reuse this trampoline for the
 * same reasons.
 */
@Component
public class BulkDeleteChunkRunner {

  @Transactional(rollbackFor = Exception.class)
  public <T> T call(Supplier<T> work) {
    return work.get();
  }

  /**
   * Same as {@link #call(Supplier)} with an explicit tenant scope. The {@code TxCtx} parameter is
   * how the transaction aspect sets {@code app.current_tenants} for this chunk, so tenant-active
   * tables (e.g. {@code autonomous_runs}) are visible to the deleter.
   */
  @Transactional(rollbackFor = Exception.class)
  public <T> T call(TxCtx ctx, Supplier<T> work) {
    return work.get();
  }
}
