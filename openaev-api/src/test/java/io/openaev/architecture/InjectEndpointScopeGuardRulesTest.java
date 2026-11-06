package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import io.openaev.architecture.fixture.InjectEndpointScopeGuardFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves the inject scope guard rule fires on a fixture, accepts each shape of guard and ignores
 * what is not a by-id inject endpoint. {@link InjectEndpointScopeGuardArchTest} runs the same rule
 * over production.
 */
@DisplayName("Inject scope guard rule: it selects by-id inject endpoints and flags the unguarded")
class InjectEndpointScopeGuardRulesTest {

  private static final JavaClasses FIXTURE_CLASSES =
      new ClassFileImporter().importClasses(InjectEndpointScopeGuardFixtures.class);

  @Nested
  @DisplayName("Selection")
  class Selection {

    @Test
    @DisplayName(
        "given the fixtures, should select the by-id INJECT, ATOMIC_TESTING and INJECT_SECRET endpoints only")
    void given_fixtures_should_selectByIdInjectEndpointsOnly() {
      // Act
      List<String> selected =
          FIXTURE_CLASSES.stream()
              .flatMap(javaClass -> javaClass.getMethods().stream())
              .filter(InjectEndpointScopeGuardRules.byIdInjectEndpoints())
              .map(JavaMethod::getName)
              .toList();

      // Assert: no resource id, another resource type and a plain method are left out
      assertThat(selected)
          .containsExactlyInAnyOrder(
              "guardedDirectly",
              "guardedThroughAHelper",
              "guardedSecret",
              "guardedInALambda",
              "guardedThroughAMethodReference",
              "unguarded",
              "guardTooFarAway");
    }
  }

  @Nested
  @DisplayName("The rule")
  class TheRule {

    @Test
    @DisplayName(
        "given a direct call, a helper, a lambda and a method reference, should accept them")
    void given_everyShapeOfGuard_should_acceptIt() {
      // Arrange
      List<String> guarded =
          List.of(
              "guardedDirectly",
              "guardedThroughAHelper",
              "guardedSecret",
              "guardedInALambda",
              "guardedThroughAMethodReference");

      // Act
      List<String> reaching =
          FIXTURE_CLASSES.stream()
              .flatMap(javaClass -> javaClass.getMethods().stream())
              .filter(method -> guarded.contains(method.getName()))
              .filter(InjectEndpointScopeGuardRules::reachesAScopeGuard)
              .map(JavaMethod::getName)
              .toList();

      // Assert
      assertThat(reaching).containsExactlyInAnyOrderElementsOf(guarded);
    }

    @Test
    @DisplayName(
        "given an endpoint calling the unscoped lookup and one whose guard is too far away,"
            + " should flag exactly those two")
    void given_unguardedAndDistantGuard_should_flagExactlyThose() {
      // Act
      EvaluationResult result =
          methods()
              .that(InjectEndpointScopeGuardRules.byIdInjectEndpoints())
              .should(InjectEndpointScopeGuardRules.reachAScopeGuard())
              .evaluate(FIXTURE_CLASSES);

      // Assert
      assertThat(result.getFailureReport().getDetails())
          .hasSize(2)
          .anyMatch(detail -> detail.contains("InjectEndpointScopeGuardFixtures#unguarded "))
          .anyMatch(detail -> detail.contains("InjectEndpointScopeGuardFixtures#guardTooFarAway "));
    }
  }
}
