package io.openaev.aop.audit_log;

import io.openaev.database.model.Action;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("!'${openaev.audit-logs.transports:}'.isEmpty()")
@RequiredArgsConstructor
public class AuditRequestValidator {
  public boolean shouldSkip(Action action) {
    return switch (action) {
      case CREATE, WRITE, DELETE, LAUNCH, DUPLICATE -> false;
      // READ/SEARCH are never audited on success — only unauthorized attempts are logged
      // (captured separately via logAuthEvent when RBAC denies access).
      case READ, SEARCH -> true;
      default -> true; // SKIP_RBAC, PROCESS
    };
  }
}
