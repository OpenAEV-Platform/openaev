package io.openaev.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Disables the Hibernate tenant filter for the annotated scope. Can be applied at:
 *
 * <ul>
 *   <li><strong>Class level</strong> — all public methods in the class run cross-tenant
 *   <li><strong>Method level</strong> — only that method runs cross-tenant
 * </ul>
 *
 * <p>This removes the need to manually call {@code
 * entityManager.unwrap(Session.class).disableFilter("tenantFilter")}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CrossTenant {}

