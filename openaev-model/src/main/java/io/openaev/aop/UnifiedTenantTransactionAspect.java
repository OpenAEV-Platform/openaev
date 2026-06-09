package io.openaev.aop;

import io.openaev.context.ExecState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UnifiedTenantTransactionAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around(
            "@annotation(org.springframework.transaction.annotation.Transactional) || "
                    + "@annotation(jakarta.transaction.Transactional)")
    public Object injectTenantFromExecState(ProceedingJoinPoint pjp) throws Throwable {

        ExecState state = findExecState(pjp.getArgs());
        if (state == null) {
            throw new IllegalStateException("No ExecState found in the method arguments");
        }

        List<String> tenants = state.restrictedTenantIds();
        if (!tenants.isEmpty()) {
            String joinedTenants = String.join(",", tenants);
            entityManager.createNativeQuery("SET LOCAL app.current_tenants = :tenantIds")
                    .setParameter("tenantIds", joinedTenants)
                    .executeUpdate();
        }

        return pjp.proceed();
    }

    private ExecState findExecState(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof ExecState execState) {
                return execState;
            }
        }
        return null;
    }
}