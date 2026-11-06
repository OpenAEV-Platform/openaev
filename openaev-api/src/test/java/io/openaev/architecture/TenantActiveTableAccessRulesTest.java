package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import io.openaev.architecture.fixture.TenantActiveTableAccessFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves the repository access rules of {@link TenantActiveTableAccessArchTest} fire on an accessor
 * they do not allowlist. The arch test runs them over production, where the allowlists keep them
 * green; here they run over fixtures, so a pass can only come from a REAL violation and never from
 * ArchUnit's empty-should error.
 */
@DisplayName("Tenant-active repository access rules: each one fires on an unreviewed accessor")
class TenantActiveTableAccessRulesTest {

  private static EvaluationResult evaluate(ArchRule rule, Class<?> fixture) {
    return rule.evaluate(new ClassFileImporter().importClasses(fixture));
  }

  @Nested
  @DisplayName("autonomous_runs")
  class AutonomousRuns {

    @Test
    @DisplayName("given an accessor outside the allowlist, should violate")
    void given_anAccessorOutsideTheAllowlist_should_violate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest.autonomous_runs_repository_access_is_reviewed,
              TenantActiveTableAccessFixtures.UnreviewedAutonomousRunAccessor.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("UnreviewedAutonomousRunAccessor")
          .contains("AutonomousRunRepository");
    }
  }

  @Nested
  @DisplayName("autonomous_objective_templates")
  class AutonomousObjectiveTemplates {

    @Test
    @DisplayName("given an accessor outside the allowlist, should violate")
    void given_anAccessorOutsideTheAllowlist_should_violate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest
                  .autonomous_objective_templates_repository_access_is_reviewed,
              TenantActiveTableAccessFixtures.UnreviewedObjectiveTemplateAccessor.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("UnreviewedObjectiveTemplateAccessor")
          .contains("AutonomousObjectiveTemplateRepository");
    }
  }

  @Nested
  @DisplayName("parameters platform row writers")
  class ParametersPlatformRowWriters {

    @Test
    @DisplayName("given a writer reached from no marker, should violate")
    void given_aWriterReachedFromNoMarker_should_violate() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest
                  .parameters_platform_row_writers_run_under_platform_write,
              TenantActiveTableAccessFixtures.UnmarkedPlatformRowWriter.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("UnmarkedPlatformRowWriter.write")
          .contains("UnmarkedPlatformRowWriter.entry");
    }

    @Test
    @DisplayName("given a writer with one unmarked caller, should violate on that caller only")
    void given_aWriterWithOneUnmarkedCaller_should_violateOnThatCallerOnly() {
      // Act
      EvaluationResult result =
          evaluate(
              TenantActiveTableAccessArchTest
                  .parameters_platform_row_writers_run_under_platform_write,
              TenantActiveTableAccessFixtures.PlatformRowWriterWithAnUnmarkedCaller.class);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("unmarkedEntry")
          .doesNotContain(".markedEntry(");
    }

    @Test
    @DisplayName("given writers under the annotation or the primitive, should not violate")
    void given_writersUnderTheAnnotationOrThePrimitive_should_notViolate() {
      // Act
      EvaluationResult annotated =
          evaluate(
              TenantActiveTableAccessArchTest
                  .parameters_platform_row_writers_run_under_platform_write,
              TenantActiveTableAccessFixtures.PlatformRowWriterUnderTheAnnotation.class);
      EvaluationResult primitive =
          evaluate(
              TenantActiveTableAccessArchTest
                  .parameters_platform_row_writers_run_under_platform_write,
              TenantActiveTableAccessFixtures.PlatformRowWriterUnderThePrimitive.class);

      // Assert
      assertThat(annotated.hasViolation()).as(annotated.getFailureReport().toString()).isFalse();
      assertThat(primitive.hasViolation()).as(primitive.getFailureReport().toString()).isFalse();
    }
  }
}
