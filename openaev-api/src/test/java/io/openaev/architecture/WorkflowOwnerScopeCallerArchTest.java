package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.openaev.service.chaining.WorkflowOwnerScope;

/**
 * Pins who may move a request's transaction into the tenant of a simulation or scenario: {@link
 * WorkflowOwnerScope} alone calls the move, and only the reasoned allowlist of {@link
 * WorkflowOwnerScopeCallerRules#OWNER_SCOPE_CALLERS} calls {@link WorkflowOwnerScope}. The list is
 * exact: a new caller fails the rule, and an entry whose class no longer calls it fails the
 * inventory, so the list never keeps a slot nobody uses.
 *
 * <p>{@link WorkflowOwnerScopeCallerRulesTest} proves each rule fires, on fixtures.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class WorkflowOwnerScopeCallerArchTest {

  @ArchTest
  static final ArchRule only_owner_scope_moves_the_current_transaction =
      WorkflowOwnerScopeCallerRules.ONLY_OWNER_SCOPE_MOVES_THE_CURRENT_TRANSACTION;

  @ArchTest
  static final ArchRule owner_scope_is_called_only_from_the_allowlist =
      WorkflowOwnerScopeCallerRules.ownerScopeCalledOnlyFrom(
          WorkflowOwnerScopeCallerRules.ownerScopeCallerNames());

  @ArchTest
  static void given_productionClasses_should_callOwnerScopeFromExactlyTheAllowlist(
      JavaClasses classes) {
    assertThat(WorkflowOwnerScopeCallerRules.ownerScopeCallers(classes))
        .as("the classes calling WorkflowOwnerScope, against the allowlist")
        .containsExactlyInAnyOrderElementsOf(WorkflowOwnerScopeCallerRules.ownerScopeCallerNames());
  }

  @ArchTest
  static void given_productionClasses_should_moveTheTransactionThroughOwnerScope(
      JavaClasses classes) {
    // Without a call left in WorkflowOwnerScope, the first rule would hold on nothing.
    assertThat(
            classes.get(WorkflowOwnerScope.class).getCodeUnitAccessesFromSelf().stream()
                .filter(WorkflowOwnerScopeCallerRules::movesTheCurrentTransaction))
        .as("WorkflowOwnerScope calls TenantScopedTransaction.executeOnCurrentTransaction")
        .isNotEmpty();
  }
}
