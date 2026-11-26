package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.openaev.architecture.fixture.TenantFilterRemovedFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves the tenant filter guard fires on each shape of filter declaration and filter call, and
 * stays quiet on code that does neither. {@link TenantFilterRemovedArchTest} runs the same rules
 * over production.
 */
@DisplayName("Tenant filter guard: it fires on a declaration or a call, and only on those")
class TenantFilterRemovedRulesTest {

  private static final String FIXTURES = TenantFilterRemovedFixtures.class.getName() + "$";

  private static final JavaClasses FIXTURE_CLASSES =
      new ClassFileImporter().importPackagesOf(TenantFilterRemovedFixtures.class);

  private static List<String> violatingClasses(ArchRule rule) {
    return rule.evaluate(FIXTURE_CLASSES).getFailureReport().getDetails().stream()
        .map(detail -> detail.substring(detail.indexOf(FIXTURES)))
        .map(detail -> detail.substring(FIXTURES.length()))
        .map(detail -> detail.replaceAll("[^A-Za-z].*", ""))
        .distinct()
        .sorted()
        .toList();
  }

  @Nested
  @DisplayName("Declarations")
  class Declarations {

    @Test
    @DisplayName("given_filterAnnotations_should_flagThemAndNothingElse")
    void given_filterAnnotations_should_flagThemAndNothingElse() {
      // Act
      List<String> flagged =
          violatingClasses(TenantFilterRemovedArchTest.no_class_declares_a_hibernate_filter);

      // Assert
      assertThat(flagged)
          .containsExactly(
              "DeclaresAFilter",
              "DeclaresAFilterContainer",
              "DeclaresAFilterDefinition",
              "DeclaresAFilterDefinitionContainer",
              "DeclaresAFilterOnAJoinTable",
              "DeclaresAJoinTableFilterContainer");
    }
  }

  @Nested
  @DisplayName("Calls")
  class Calls {

    @Test
    @DisplayName("given_everyShapeOfTheCall_should_flagEachCallerAndNotAPlainSessionUse")
    void given_everyShapeOfTheCall_should_flagEachCallerAndNotAPlainSessionUse() {
      // Act
      List<String> flagged =
          violatingClasses(TenantFilterRemovedArchTest.no_class_arms_or_reads_a_session_filter);

      // Assert: any session type, the session factory and a method reference count, a plain session
      // call does not
      assertThat(flagged)
          .containsExactly(
              "DisablesAFilterThroughASubtype",
              "EnablesAFilter",
              "EnablesAFilterThroughAMethodReference",
              "ReadsAFilterOnAStatelessSession",
              "ReadsTheDefinedFilterNamesOfAFactory");
    }
  }
}
