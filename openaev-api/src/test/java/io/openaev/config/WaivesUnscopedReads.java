package io.openaev.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Waives named unscoped reads for the one test, or the one class, that carries it. Each entry has
 * the shape of a line of {@code failclosed-baseline.txt} limited to tables ({@code
 * io.openaev.Foo.bar tables=t1,t2}), and {@link FailClosedGateExtension} applies it to this test
 * only: any other production read with no scope in the same test fails it, and the same site
 * reached with no scope from any other test fails that test.
 *
 * <p>It is for a test whose subject is a caller or a path with no scope, such as a member of no
 * tenant, and which asserts a positive outcome besides the empty reads it names: {@link
 * ExpectsEmptyScopeReads} switches the gate off for the whole test, and a baseline line waives its
 * site for every test. An entry with no {@code tables=} is refused, so an entry never waives a
 * whole site.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface WaivesUnscopedReads {

  /** Why each named read is empty with no scope, and why the test needs no rows from it. */
  String value();

  /** The waived reads, one {@code class.method tables=t1,t2} entry each. */
  String[] reads();
}
