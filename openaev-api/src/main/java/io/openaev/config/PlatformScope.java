package io.openaev.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code TxCtx} controller parameter as needing only a scope sufficient to see platform
 * rows ({@code tenant_id IS NULL} on a dual-scope table), independent of the caller's identity or
 * tenant selector: an anonymous, pre-login endpoint whose data has no tenant boundary at all (e.g.
 * {@code PlatformSettingsApi.publicSettings}).
 *
 * <p>{@code can_access_tenant(tenant_id, true)} lets a platform row through under ANY non-empty
 * {@code app.current_tenants} scope, regardless of which tenant is named, so this resolves to
 * {@link io.openaev.database.model.Tenant#DEFAULT_TENANT_UUID} rather than to the caller's
 * (possibly nonexistent, for an anonymous caller) memberships. This is a READ-only scope: it does
 * not make the transaction platform-write (see {@link io.openaev.aop.PlatformWrite}), so it never
 * lets a write reach a platform row by itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PlatformScope {}
