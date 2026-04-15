package io.openaev.rest.audit_log;

import io.openaev.engine.model.auditlog.LogEvent;
import io.openaev.utils.pagination.SearchPaginationInput;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * Translates search/filter input into an engine query and returns paginated {@link LogEvent}
 * results from the audit-log index.
 *
 * <p>TODO (Task 12): implement query building, tenant isolation, and engine search delegation.
 */
@Service
@RequiredArgsConstructor
public class AuditLogSearchService {

  /**
   * Searches audit log events with pagination and filtering.
   *
   * @param input pagination and filter parameters
   * @return paginated {@link LogEvent} results
   */
  public Page<LogEvent> search(SearchPaginationInput input) {
    // TODO: implement — delegate to EngineService.searchIndex()
    throw new UnsupportedOperationException("Audit log search not yet implemented (Task 12)");
  }
}
