package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import io.openaev.architecture.fixture.CallerRightsOutsideAllowlistFixture;
import io.openaev.architecture.fixture.CurrentTransactionMoveFixture;
import io.openaev.architecture.fixture.OwnerScopeOutsideAllowlistFixture;
import io.openaev.architecture.fixture.OwnerScopeSubclassFixture;
import io.openaev.architecture.fixture.ScopeSetterOutsideBaselineFixture;
import io.openaev.service.PermissionService;
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
    @DisplayName("an unlisted class calling the move violates the rule")
    void given_directCallerOfTheMove_should_violateTheRule() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(CurrentTransactionMoveFixture.class);

      // Act
      EvaluationResult result =
          WorkflowOwnerScopeCallerRules.ONLY_LISTED_CLASSES_MOVE_THE_CURRENT_TRANSACTION.evaluate(
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

  @Nested
  @DisplayName("Loading the caller's rights in the ambient tenant")
  class LoadingTheCallersRightsInTheAmbientTenant {

    @Test
    @DisplayName("a call and a method reference to CallerRightsInTenant from an unlisted class")
    void given_unlistedCallerOfTheLoader_should_violateTheRuleForEachAccess() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(CallerRightsOutsideAllowlistFixture.class);

      // Act
      EvaluationResult result =
          WorkflowOwnerScopeCallerRules.callerRightsLoadedOnlyFrom(Set.of()).evaluate(fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().getDetails())
          .anyMatch(line -> line.contains("rightsOf"))
          .anyMatch(line -> line.contains("rightsAsReference"))
          .noneMatch(line -> line.contains("ambientCaller"));
    }

    @Test
    @DisplayName("an allowlisted class passes the loader rule")
    void given_allowlistedCallerOfTheLoader_should_passTheRule() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(CallerRightsOutsideAllowlistFixture.class);

      // Act
      EvaluationResult result =
          WorkflowOwnerScopeCallerRules.callerRightsLoadedOnlyFrom(
                  Set.of(CallerRightsOutsideAllowlistFixture.class.getName()))
              .evaluate(fixture);

      // Assert
      assertThat(result.hasViolation()).isFalse();
    }

    @Test
    @DisplayName("a call and a method reference to callerInAmbientTenant from an unlisted class")
    void given_unlistedCallerOfTheAmbientCaller_should_violateTheRuleForEachAccess() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(CallerRightsOutsideAllowlistFixture.class);

      // Act
      EvaluationResult result =
          WorkflowOwnerScopeCallerRules.ambientCallerReadOnlyFrom(Set.of()).evaluate(fixture);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().getDetails())
          .anyMatch(line -> line.contains("ambientCaller("))
          .anyMatch(line -> line.contains("ambientCallerAsReference"))
          .noneMatch(line -> line.contains("rightsOf"));
    }

    @Test
    @DisplayName("PermissionService itself and an allowlisted class pass the ambient caller rule")
    void given_allowlistedCallerOfTheAmbientCaller_should_passTheRule() {
      // Arrange
      JavaClasses classes =
          new ClassFileImporter()
              .importClasses(CallerRightsOutsideAllowlistFixture.class, PermissionService.class);

      // Act
      EvaluationResult result =
          WorkflowOwnerScopeCallerRules.ambientCallerReadOnlyFrom(
                  Set.of(CallerRightsOutsideAllowlistFixture.class.getName()))
              .evaluate(classes);

      // Assert
      assertThat(result.hasViolation()).isFalse();
    }

    @Test
    @DisplayName("the inventories name the calling class once, method references included")
    void given_callerWithCallsAndReferences_should_beInventoriedOnce() {
      // Arrange
      JavaClasses fixture =
          new ClassFileImporter().importClasses(CallerRightsOutsideAllowlistFixture.class);

      // Act
      Set<String> loaders = WorkflowOwnerScopeCallerRules.callerRightsLoaders(fixture);
      Set<String> readers = WorkflowOwnerScopeCallerRules.ambientCallerReaders(fixture);

      // Assert
      assertThat(loaders).containsExactly(CallerRightsOutsideAllowlistFixture.class.getName());
      assertThat(readers).containsExactly(CallerRightsOutsideAllowlistFixture.class.getName());
    }
  }
}
