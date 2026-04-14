package io.openaev.rest.audit_log;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogApi extends RestBehavior {

  private final AuditLogSearchService auditLogSearchService;

  // -- READ --

  @LogExecutionTime
  @PostMapping("/search")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.AUDIT_LOG)
  @Operation(summary = "Paginated search on audit logs")
  public Page<AuditLogOutput> search(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return auditLogSearchService.search(searchPaginationInput).map(AuditLogMapper::toOutput);
  }
}
