package io.openaev.architecture;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class as the isolation test of the tenant tables it names, for {@link
 * TenantIsolationCoverageArchTest}. The gate fails the build when a table of the production {@code
 * openaev.tenant.active-tables} list has no marked class and no line in {@code
 * tenant-isolation-coverage-baseline.txt}.
 *
 * <p>A class earns the marker for a table when all of this holds:
 *
 * <ul>
 *   <li>it runs on the real stack: Postgres and the statement inspector, not a mock of either;
 *   <li>its effective activation list holds the table: the production list, or a {@code probe} pin
 *       (see {@code pinned-active-tables-baseline.txt}) that includes it. A class recorded there as
 *       {@code drifts} or {@code control} does not count;
 *   <li>it seeds two tenants and asserts a cross-tenant refusal on that table: a caller scoped to
 *       one tenant neither reads, nor changes, nor deletes the other tenant's row.
 * </ul>
 *
 * <p>The gate checks the marker and the effective list: each marked table is active, each marked
 * table is in the class's effective list, and the class is not a {@code drifts} or {@code control}
 * line. It does not read the test body. "Two tenants and a cross-tenant refusal" rests on whoever
 * adds the marker, and on the reviewer of that change. A class with no pin and no registration is
 * counted as running the production list, which the CI arming of {@code
 * -Dopenaev.tenant.active-tables} provides and the gate cannot verify.
 *
 * <p>The value is an array so a table can later require a second case, such as the platform-row
 * write of a dual-scope table, without changing the marker's shape.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CoversTenantIsolation {

  /** The tables this class is the isolation test of, as they are named in the activation list. */
  String[] value();
}
