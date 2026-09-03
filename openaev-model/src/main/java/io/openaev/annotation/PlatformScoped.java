package io.openaev.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an API controller endpoint (or a whole controller) operates on platform or
 * dual-scope data (roles, groups, settings, admin/cross-tenant surfaces) through a different
 * mechanism, so a tenant {@code TxCtx} does not apply and would be a silent no-op. It is one of the
 * two escape hatches the default-secure compile-time rule ({@code EndpointTxScopeRule}, #7726)
 * accepts in place of a {@code TxCtx} parameter. Use {@link NoTenantScope} instead for
 * public/anonymous endpoints. Retention is SOURCE: the only consumer is the compile-time processor.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface PlatformScoped {}
