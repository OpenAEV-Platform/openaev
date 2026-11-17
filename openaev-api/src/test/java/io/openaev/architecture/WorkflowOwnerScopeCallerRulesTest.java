package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import io.openaev.architecture.fixture.CurrentTransactionMoveFixture;
import io.openaev.architecture.fixture.OwnerScopeOutsideAllowlistFixture;
import io.openaev.architecture.fixture.OwnerScopeSubclassFixture;
import io.openaev.architecture.fixture.ScopeSetterOutsideBaselineFixture;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves the rules of {@link WorkflowOwnerScopeCallerArchTest} fire, against fixtures that break
 * them, and that an allowlisted caller passes. Evaluated through {@code hasViolation()} so a pass
 * can only come from a real violation.
 */
@DisplayName(
    "Owner scope caller guards: each rule fires, and the allowlist lets its callers through")
class WorkflowOwnerScopeCallerRulesTest {

  @Nested
  @DisplayName("Moving the current transaction")
  class MovingTheCurrentTransaction {

    @Test
    @DisplayName("a class other than WorkflowOwnerScope calling the move violates the rule")
    void given_directCallerOfTheMove_should_violateTheRule() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(CurrentTransactionMoveFixture.class);

      // Act
      EvaluationResult result =
          WorkflowOwnerScopeCallerRules.ONLY_OWNER_SCOPE_MOVES_THE_CURRENT_TRANSACTION.evaluate(
              fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString())
          .contains("CurrentTransactionMoveFixture")
          .contains(WorkflowOwnerScopeCallerRules.MOVE_METHOD);
    }
  }

  @Nested
  @DisplayName("Calling WorkflowOwnerScope")
  class CallingWorkflowOwnerScope {

    @Test
    @DisplayName("a call and a method reference from an unlisted class both violate the rule")
    void given_unlistedCaller_should_violateTheRuleForEachAccess() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(OwnerScopeOutsideAllowlistFixture.class);

      // Act
      EvaluationResult result =
          WorkflowOwnerScopeCallerRules.ownerScopeCalledOnlyFrom(Set.of()).evaluate(fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().getDetails())
          .anyMatch(line -> line.contains("runInOwnerTenant"))
          .anyMatch(line -> line.contains("asReference"));
    }

    @Test
    @DisplayName("a call through a subclass of WorkflowOwnerScope violates the rule")
    void given_callThroughASubclass_should_violateTheRule() {
      // Arrange
      JavaClasses fixture = new ClassFileImporter().importClasses(OwnerScopeSubclassFixture.class);

      // Act
      EvaluationResult result =
          WorkflowOwnerScopeCallerRules.ownerScopeCalledOnlyFrom(Set.of()).evaluate(fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().toString()).contains("OwnerScopeSubclassFixture");
    }

    @Test
    @DisplayName("an allowlisted class passes the rule")
    void given_allowlistedCaller_should_passTheRule() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(OwnerScopeOutsideAllowlistFixture.class);

      // Act
      EvaluationResult result =
          WorkflowOwnerScopeCallerRules.ownerScopeCalledOnlyFrom(
                  Set.of(OwnerScopeOutsideAllowlistFixture.class.getName()))
              .evaluate(fixture);

      // Assert
      assertThat(result.hasViolation()).isFalse();
    }

    @Test
    @DisplayName("the inventory names the calling class once, lambdas included")
    void given_callerWithALambdaAndAReference_should_beInventoriedOnce() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(OwnerScopeOutsideAllowlistFixture.class);

      // Act
      Set<String> callers = WorkflowOwnerScopeCallerRules.ownerScopeCallers(fixture);

      // Assert
      assertThat(callers).containsExactly(OwnerScopeOutsideAllowlistFixture.class.getName());
    }
  }

  @Nested
  @DisplayName("Setting the scope of the current transaction")
  class SettingTheScopeOfTheCurrentTransaction {

    @Test
    @DisplayName("a call and a method reference from a class outside the baseline both violate")
    void given_callerOutsideTheBaseline_should_violateTheRuleForEachAccess() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(ScopeSetterOutsideBaselineFixture.class);

      // Act
      EvaluationResult result =
          WorkflowOwnerScopeCallerRules.scopeSetOnCurrentTransactionOnlyFrom(Set.of())
              .evaluate(fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().getDetails())
          .anyMatch(line -> line.contains("moveTo"))
          .anyMatch(line -> line.contains("asReference"));
    }

    @Test
    @DisplayName("a class in the baseline passes the rule")
    void given_callerInTheBaseline_should_passTheRule() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(ScopeSetterOutsideBaselineFixture.class);

      // Act
      EvaluationResult result =
          WorkflowOwnerScopeCallerRules.scopeSetOnCurrentTransactionOnlyFrom(
                  Set.of(ScopeSetterOutsideBaselineFixture.class.getName()))
              .evaluate(fixture);

      // Assert
      assertThat(result.hasViolation()).isFalse();
    }

    @Test
    @DisplayName("the inventory names the calling class once, method references included")
    void given_callerWithACallAndAReference_should_beInventoriedOnce() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(ScopeSetterOutsideBaselineFixture.class);

      // Act
      Set<String> callers = WorkflowOwnerScopeCallerRules.scopeSetters(fixture);

      // Assert
      assertThat(callers).containsExactly(ScopeSetterOutsideBaselineFixture.class.getName());
    }
  }
}
