package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.openaev.service.chaining.WorkflowOwnerScope;

/**
 * Pins who may change the tenant scope of a transaction already open: only the reasoned allowlist
 * of {@link WorkflowOwnerScopeCallerRules#TRANSACTION_MOVERS} calls {@code
 * executeOnCurrentTransaction}, only the reasoned allowlist of {@link
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
  static final ArchRule only_listed_classes_move_the_current_transaction =
      WorkflowOwnerScopeCallerRules.ONLY_LISTED_CLASSES_MOVE_THE_CURRENT_TRANSACTION;

  @ArchTest
  static void given_productionClasses_should_moveTheTransactionFromExactlyTheAllowlist(
      JavaClasses classes) {
    assertThat(WorkflowOwnerScopeCallerRules.transactionMovers(classes))
        .as("the classes calling TenantScopedTransaction.executeOnCurrentTransaction")
        .containsExactlyInAnyOrderElementsOf(WorkflowOwnerScopeCallerRules.transactionMoverNames());
  }

  @ArchTest
  static final ArchRule owner_scope_is_called_only_from_the_allowlist =
      WorkflowOwnerScopeCallerRules.ownerScopeCalledOnlyFrom(
          WorkflowOwnerScopeCallerRules.ownerScopeCallerNames());

  @ArchTest
  static final ArchRule caller_rights_are_loaded_only_from_the_allowlist =
      WorkflowOwnerScopeCallerRules.callerRightsLoadedOnlyFrom(
          WorkflowOwnerScopeCallerRules.callerRightsLoaderNames());

  @ArchTest
  static void given_productionClasses_should_loadCallerRightsFromExactlyTheAllowlist(
      JavaClasses classes) {
    assertThat(WorkflowOwnerScopeCallerRules.callerRightsLoaders(classes))
        .as("the classes calling CallerRightsInTenant, against the allowlist")
        .containsExactlyInAnyOrderElementsOf(
            WorkflowOwnerScopeCallerRules.callerRightsLoaderNames());
  }

  @ArchTest
  static final ArchRule ambient_caller_is_read_only_from_the_allowlist =
      WorkflowOwnerScopeCallerRules.ambientCallerReadOnlyFrom(
          WorkflowOwnerScopeCallerRules.ambientCallerReaderNames());

  @ArchTest
  static void given_productionClasses_should_readTheAmbientCallerFromExactlyTheAllowlist(
      JavaClasses classes) {
    assertThat(WorkflowOwnerScopeCallerRules.ambientCallerReaders(classes))
        .as("the classes calling PermissionService.callerInAmbientTenant, against the allowlist")
        .containsExactlyInAnyOrderElementsOf(
            WorkflowOwnerScopeCallerRules.ambientCallerReaderNames());
  }

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
