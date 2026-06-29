package io.openaev.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.openaev.annotation.AllowRawJdbc;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Guards the one structural assumption tenant isolation rests on: only Hibernate-emitted SQL passes
 * through the tenant statement inspector. Raw JDBC bypasses it, so a query could read or write
 * tenant data unscoped. This rule forbids new raw-JDBC access in production code; tenant access
 * must go through Hibernate. The few audited exceptions on non-tenant tables opt out explicitly
 * with {@code @AllowRawJdbc} (each carrying a reason), so any new use fails the build and forces a
 * deliberate decision. Flyway migrations are platform code and are excluded.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class TenantNonOrmAccessArchTest {

  @ArchTest
  static final ArchRule no_raw_jdbc_outside_the_allowlist =
      noClasses()
          .that()
          .resideOutsideOfPackage("io.openaev.migration..")
          // Audited exceptions opt out explicitly with @AllowRawJdbc (each carries its reason).
          .and()
          .areNotAnnotatedWith(AllowRawJdbc.class)
          .should()
          .dependOnClassesThat(
              // Spring JDBC query API by package, so the interfaces (JdbcOperations,
              // NamedParameterJdbcOperations) are caught too, not just the implementations.
              resideInAPackage("org.springframework.jdbc.core..")
                  // Raw JDBC types; Statement also covers Prepared/CallableStatement.
                  .or(assignableTo(Connection.class))
                  .or(assignableTo(Statement.class))
                  .or(assignableTo(ResultSet.class)))
          .because(
              "raw JDBC bypasses the tenant statement inspector; tenant access must go through"
                  + " Hibernate");
}
