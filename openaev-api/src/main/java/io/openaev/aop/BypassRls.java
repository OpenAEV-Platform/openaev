package io.openaev.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as requiring cross-tenant data access (bypasses Row-Level Security). Intended for
 * scheduled jobs and background tasks that process data across all tenants.
 *
 * <p>When this annotation is present, the aspect sets {@link
 * io.openaev.context.TenantContext#setRlsBypass()} before method execution and clears it
 * afterwards. This causes database connections to use {@code RESET ROLE} (superuser) instead of the
 * restricted {@code openaev_app} role, effectively disabling RLS policies.
 *
 * <p><strong>Security note:</strong> Only use on internal scheduled jobs — never on REST controller
 * methods.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BypassRls {}
