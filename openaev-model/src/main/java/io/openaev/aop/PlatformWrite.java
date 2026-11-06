package io.openaev.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @Transactional} method (HTTP controller or service) as a platform-write
 * transaction: {@link TenantScopeTransactionAspect} sets the {@code app.platform_write}
 * transaction-local setting for its duration, which is what {@code can_write_tenant} checks before
 * letting a write touch a dual-scope table's platform row ({@code tenant_id IS NULL}).
 *
 * <p>Only three kinds of call site may carry this annotation: a platform-admin endpoint already
 * gated by {@code @AccessControl} on a platform capability (never a tenant route), or a startup
 * path with no caller to gate at all (see {@code TenantScopedTransaction#executeAsPlatformWrite}
 * for the background-primitive equivalent, used off the request thread). A migration needs neither:
 * it runs outside Hibernate, so the statement inspector never sees it.
 *
 * <p>Adding this to a method does not by itself authorize anything — {@code @AccessControl} still
 * decides who may call it. This annotation only lifts the SQL-level refusal of a platform-row write
 * for a transaction the server has already decided is allowed to make one.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PlatformWrite {}
