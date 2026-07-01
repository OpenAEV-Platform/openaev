package io.openaev.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an audited, deliberate exception to the no-raw-JDBC rule (enforced by {@code
 * TenantNonOrmAccessArchTest}). Raw JDBC bypasses the tenant statement inspector, so it is
 * forbidden in production code by default; this annotation is the explicit, reviewable escape hatch
 * for the rare legitimate use (non-tenant tables, schema metadata). The {@code reason} must state
 * why bypassing the inspector is safe here, so the exception stays deliberate and reviewable rather
 * than hidden in a name list.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowRawJdbc {
  String reason();
}
