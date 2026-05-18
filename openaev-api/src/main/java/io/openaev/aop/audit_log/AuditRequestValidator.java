package io.openaev.aop.audit_log;

import io.openaev.database.model.Action;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditRequestValidator {

    @Value("${openaev.audit-logs.service.enabled:false}")
    private boolean enabled;

    @Value("${openaev.audit-logs.log-reads:false}")
    private boolean logReads;

    public boolean valid(Action action) {
        if (!enabled) {
            return false;
        }

        // Skip actions we don't audit
        if (shouldSkip(action)) {
            return false;
        }

        // Skip automated requests — not user-initiated actions
        if (AuditRequestFilter.isAutomatedRequest()) {
            return false;
        }

        return true;
    }

    private boolean shouldSkip(Action action) {
        return switch (action) {
            case CREATE, WRITE, DELETE, LAUNCH, DUPLICATE -> false;
            case READ, SEARCH -> !logReads;
            default -> true; // SKIP_RBAC, PROCESS
        };
    }
}
