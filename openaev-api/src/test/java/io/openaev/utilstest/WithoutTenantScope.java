package io.openaev.utilstest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a test class (or method) out of {@link DefaultTenantScopeTestListener}: its transaction
 * starts with no tenant scope at all. For tests whose premise is an unscoped read or write (the
 * fail-closed and write-attribution suites), which the default tenant scope would otherwise
 * satisfy.
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface WithoutTenantScope {}
