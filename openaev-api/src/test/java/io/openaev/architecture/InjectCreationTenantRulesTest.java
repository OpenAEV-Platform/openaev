package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import io.openaev.architecture.fixture.InjectCreationTenantFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves the inject creation rule selects the code units that create or attach an inject and flags
 * those that leave the tenant to the listener. {@link InjectCreationTenantArchTest} runs it over
 * production.
 */
@DisplayName("Inject creation rule: it flags an inject whose tenant is left to the listener")
class InjectCreationTenantRulesTest {

  private static final JavaClasses FIXTURE_CLASSES =
      new ClassFileImporter().importClasses(InjectCreationTenantFixtures.class);

  @Nested
  @DisplayName("Selection")
  class Selection {

    @Test
    @DisplayName("given the fixtures, should select the units that create or attach an inject")
    void given_fixtures_should_selectCreatingAndAttachingUnitsOnly() {
      // Act
      List<String> selected =
          FIXTURE_CLASSES.stream()
              .flatMap(javaClass -> javaClass.getCodeUnits().stream())
              .filter(InjectCreationTenantRules.createOrAttachAnInject())
              .map(JavaCodeUnit::getName)
              .toList();

      // Assert: a call made in a lambda counts for the method that holds it; the helper call
      // alone and the unrelated read are out
      assertThat(selected)
          .contains(
              "createsAndStamps",
              "createsAndLeavesTheTenantToTheListener",
              "createsAndAttachesThroughTheHelper",
              "attachesAndLeavesTheTenantToTheListener",
              "attachesInALambdaAndLeavesTheTenantToTheListener")
          .doesNotContain("attachesThroughTheHelper", "unrelated");
    }
  }

  @Nested
  @DisplayName("The rule")
  class TheRule {

    @Test
    @DisplayName(
        "given an inject created or attached with no tenant stamp, should flag exactly those units")
    void given_unstampedUnits_should_flagExactlyThose() {
      // Act
      EvaluationResult result =
          codeUnits()
              .that(InjectCreationTenantRules.createOrAttachAnInject())
              .should(InjectCreationTenantRules.alsoStampTheTenant())
              .evaluate(FIXTURE_CLASSES);

      // Assert
      assertThat(result.getFailureReport().getDetails())
          .hasSize(3)
          .anyMatch(detail -> detail.contains("#createsAndLeavesTheTenantToTheListener "))
          .anyMatch(detail -> detail.contains("#attachesAndLeavesTheTenantToTheListener "))
          .anyMatch(
              detail -> detail.contains("#attachesInALambdaAndLeavesTheTenantToTheListener "));
    }
  }
}
