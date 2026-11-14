package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static io.openaev.architecture.InjectEndpointScopeGuardRules.Outcome.GUARDED;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import io.openaev.architecture.fixture.InjectEndpointScopeGuardFixtures;
import java.util.List;
import java.util.stream.Stream;
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
        "given the fixtures, should select the by-id INJECT, ATOMIC_TESTING, INJECT_SECRET and JOB endpoints only")
    void given_fixtures_should_selectByIdInjectEndpointsOnly() {
      // Act
      List<String> selected = endpoints().map(JavaMethod::getName).toList();

      // Assert: no resource id (a JOB that takes no inject included), another resource type and a
      // plain method are left out
      assertThat(selected)
          .containsExactlyInAnyOrder(
              "guardedDirectly",
              "guardedThroughAHelper",
              "guardedSecret",
              "guardedInALambda",
              "guardedWithTheIdSecond",
              "jobGuarded",
              "jobUnguarded",
              "unguarded",
              "guardTooFarAway",
              "guardOnAnotherId",
              "guardAfterTheWrite",
              "guardAfterTheWriteInTheHelper");
    }
  }

  @Nested
  @DisplayName("The rule")
  class TheRule {

    @Test
    @DisplayName(
        "given a direct call, a helper, a lambda, an id in another position and a JOB endpoint, should accept them")
    void given_everyShapeOfGuard_should_acceptIt() {
      // Arrange
      List<String> guarded =
          List.of(
              "guardedDirectly",
              "guardedThroughAHelper",
              "guardedSecret",
              "guardedInALambda",
              "guardedWithTheIdSecond",
              "jobGuarded");

      // Act
      List<String> accepted =
          endpoints()
              .filter(method -> InjectEndpointScopeGuardRules.outcomeOf(method) == GUARDED)
              .map(JavaMethod::getName)
              .toList();

      // Assert
      assertThat(accepted).containsExactlyInAnyOrderElementsOf(guarded);
    }

    @Test
    @DisplayName(
        "given an unscoped lookup, an unguarded JOB endpoint, a guard too far away, a guard on"
            + " another id and a guard after a write, should flag exactly those, each for its own reason")
    void given_weakGuards_should_flagExactlyThose() {
      // Act
      EvaluationResult result =
          methods()
              .that(InjectEndpointScopeGuardRules.byIdInjectEndpoints())
              .should(InjectEndpointScopeGuardRules.guardTheResourceIdBeforeAnyWrite())
              .evaluate(FIXTURE_CLASSES);

      // Assert
      assertThat(result.getFailureReport().getDetails())
          .hasSize(6)
          .anyMatch(
              detail -> detail.contains("#unguarded never passes its resource id to a scope guard"))
          .anyMatch(
              detail ->
                  detail.contains("#jobUnguarded never passes its resource id to a scope guard"))
          .anyMatch(
              detail ->
                  detail.contains("#guardTooFarAway never passes its resource id to a scope guard"))
          .anyMatch(
              detail ->
                  detail.contains(
                      "#guardOnAnotherId never passes its resource id to a scope guard"))
          .anyMatch(detail -> detail.contains("#guardAfterTheWrite writes before it guards"))
          .anyMatch(
              detail -> detail.contains("#guardAfterTheWriteInTheHelper writes before it guards"));
    }
  }

  private static Stream<JavaMethod> endpoints() {
    return FIXTURE_CLASSES.stream()
        .flatMap(javaClass -> javaClass.getMethods().stream())
        .filter(InjectEndpointScopeGuardRules.byIdInjectEndpoints());
  }
}
