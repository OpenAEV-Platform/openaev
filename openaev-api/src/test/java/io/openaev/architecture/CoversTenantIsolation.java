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
 *   <li>it is a runnable test: not abstract, not disabled, with a test method;
 *   <li>it runs on the real stack: Postgres and the statement inspector, not a mock of either;
 *   <li>it declares an activation list that holds the table: a {@code probe} pin (see {@code
 *       pinned-active-tables-baseline.txt}) that includes it, or a {@code DynamicPropertySource}
 *       registering {@code ProductionActiveTables.names()}. A class that declares nothing is not
 *       armed by the gating CI run, and a class recorded there as {@code drifts} or {@code control}
 *       does not count;
 *   <li>it seeds two tenants and asserts a cross-tenant refusal (read, write or delete) on the
 *       table: a caller scoped to one tenant is refused the other tenant's row of that table, and
 *       the refusal is not reached through a parent row or by another mechanism.
 * </ul>
 *
 * <p>The gate checks the marker, the runnable test and the declared list: each marked table is
 * active, each marked table is in the class's declared list, and the class is not a {@code drifts}
 * or {@code control} line. It does not read the test body. "Two tenants and a cross-tenant refusal
 * on the table" rests on whoever adds the marker, and on the reviewer of that change.
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
