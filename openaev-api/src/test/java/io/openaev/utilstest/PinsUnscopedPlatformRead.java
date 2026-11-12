package io.openaev.utilstest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the test that reads a platform row of a dual-scope table with no scope, which is what a
 * dual-scope table armed in production must have decided. The architecture guard looks for it by
 * reflection on the test class it is given for the table: the marked method must be an enabled test
 * that runs under {@link WithoutTenantScope}, or the table is not pinned.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PinsUnscopedPlatformRead {

  /** The dual-scope table whose platform row the test reads with no scope. */
  String table();
}
