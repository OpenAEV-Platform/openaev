package io.openaev.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code TxCtx} controller parameter as needing a single-tenant scope: the endpoint
 * performs a composite-PK lookup or attributes rows, so operating across every tenant of the caller
 * by accident would be wrong. The scope is taken from the explicit selector when present (a {@code
 * /{tenantId}} path or an {@code X-Tenant-Ids} header); without one the request is NOT refused but
 * falls back: a single-tenant caller (every Community Edition deployment, single-tenant users in
 * EE) resolves to its own tenant, a multi-tenant caller falls back to the default tenant (the
 * platform-wide convention for requests without an explicit tenant context). Only a multi-tenant
 * caller without access to the default tenant is refused (400), the one case where no fallback is
 * safe. The default (no annotation) keeps the composable semantics where an absent selector means
 * the full allowed set.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequireTenantSelector {}
