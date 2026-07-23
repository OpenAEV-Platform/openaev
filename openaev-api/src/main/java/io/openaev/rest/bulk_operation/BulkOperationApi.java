package io.openaev.rest.bulk_operation;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.context.TenantContext;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.utils.BulkOperationMonitor;
import io.openaev.service.utils.BulkOperationMonitor.BulkOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the massive (bulk) operations of the current user (running plus recent history), so the
 * frontend can seed its permanent header indicator on page load and after a reconnect. Live updates
 * flow through the {@code bulk-operation} SSE events, which are also delivered per user.
 */
@RestController
@RequiredArgsConstructor
public class BulkOperationApi extends RestBehavior {

  public static final String BULK_OPERATION_URI = "/api/bulk-operations";

  private final BulkOperationMonitor bulkOperationMonitor;

  @GetMapping({BULK_OPERATION_URI, TENANT_PREFIX + "/bulk-operations"})
  @Transactional(propagation = Propagation.SUPPORTS)
  // Scoped to the caller's own operations and exposes only aggregate counts and entity labels,
  // never entity data: no per-resource RBAC applies.
  @AccessControl(skipRBAC = true)
  public List<BulkOperation> bulkOperations() {
    return bulkOperationMonitor.findForUser(
        currentUser().getId(), TenantContext.getCurrentTenant());
  }
}
