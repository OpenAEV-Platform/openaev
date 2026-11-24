package io.openaev.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test whose subject is a caller or a path with no tenant scope, such as a caller who
 * belongs to no tenant. The production code it calls reads an active table with an empty scope on
 * purpose, and the result it checks is the empty answer, so {@link FailClosedGateExtension} does
 * not fail it for those reads.
 *
 * <p>Put it on the one test method, or the one class, whose premise is the absence of a scope. A
 * test that needs data from the active tables gives its caller a membership instead.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ExpectsEmptyScopeReads {

  /** What the test pins and why the empty read is the right answer. */
  String value();
}
