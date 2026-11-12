package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.openaev.service.chaining.WorkflowOwnerScope;

/**
 * Pins who may change the tenant scope of a transaction already open: {@link WorkflowOwnerScope}
 * alone calls {@code executeOnCurrentTransaction}, only the reasoned allowlist of {@link
 * WorkflowOwnerScopeCallerRules#OWNER_SCOPE_CALLERS} calls {@link WorkflowOwnerScope}, and only the
 * baseline of {@link WorkflowOwnerScopeCallerRules#SCOPE_SETTERS} calls {@code
 * setScopeOnCurrentTransaction}. Each list is exact: a new caller fails its rule, and an entry
 * whose class no longer calls fails the inventory, so no list keeps a slot nobody uses. The
 * baseline's size is frozen so that it can only shrink.
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

  /** Lowered when a caller leaves the baseline, never raised. */
  private static final int FROZEN_SCOPE_SETTERS = 10;

  @ArchTest
  static final ArchRule scope_is_set_on_the_current_transaction_only_from_the_baseline =
      WorkflowOwnerScopeCallerRules.scopeSetOnCurrentTransactionOnlyFrom(
          WorkflowOwnerScopeCallerRules.scopeSetterNames());

  @ArchTest
  static void given_productionClasses_should_setTheScopeFromExactlyTheBaseline(
      JavaClasses classes) {
    assertThat(WorkflowOwnerScopeCallerRules.scopeSetters(classes))
        .as("the classes calling TenantScopedTransaction.setScopeOnCurrentTransaction")
        .containsExactlyInAnyOrderElementsOf(WorkflowOwnerScopeCallerRules.scopeSetterNames());
    assertThat(WorkflowOwnerScopeCallerRules.SCOPE_SETTERS)
        .as("the baseline only shrinks: remove the entry and lower the frozen size together")
        .hasSize(FROZEN_SCOPE_SETTERS);
  }

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
