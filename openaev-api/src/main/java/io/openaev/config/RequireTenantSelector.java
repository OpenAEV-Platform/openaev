package io.openaev.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code TxCtx} controller parameter as requiring an explicit tenant selector on the
 * request: a {@code /{tenantId}} path or an {@code X-Tenant-Ids} header. Without one, the request
 * is refused (400) instead of falling back to the caller's full set of allowed tenants. Use it on
 * endpoints where operating across every tenant of the caller by accident would be wrong, typically
 * writes or single-tenant operations. The default (no annotation) keeps the composable semantics
 * where an absent selector means the full allowed set.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequireTenantSelector {}
