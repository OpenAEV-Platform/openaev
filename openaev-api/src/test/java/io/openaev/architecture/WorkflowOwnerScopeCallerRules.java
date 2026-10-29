package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.integration.ManagerCreator;
import io.openaev.opencti.connectors.service.OpenCTIConnectorPrivilegeService;
import io.openaev.processor.MigrationProcessor;
import io.openaev.rest.exercise.ExerciseApi;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.exercise.service.ExportService;
import io.openaev.rest.scenario.ScenarioApi;
import io.openaev.runner.InitAdminCommandLineRunner;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.scheduler.jobs.QueueChainingJob;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.chaining.WorkflowOwnerScope;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.xtmhub.XtmHubService;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Who may change the tenant scope of a transaction that is already open.
 *
 * <p>Two methods of {@link TenantScopedTransaction} do it, and neither checks the tenant it is
 * given against the scope the transaction was opened with:
 *
 * <ul>
 *   <li>{@link TenantScopedTransaction#executeOnCurrentTransaction} moves the transaction to one
 *       tenant for the duration of some work. {@link WorkflowOwnerScope} is its only allowed
 *       caller, and passes the tenant of a simulation or scenario. That is safe only when the
 *       simulation or scenario was loaded through the request's own access check, so the callers of
 *       {@link WorkflowOwnerScope} are an allowlist, each with the reason the owner it passes was
 *       loaded that way.
 *   <li>{@link TenantScopedTransaction#setScopeOnCurrentTransaction} sets the scope for the rest of
 *       the transaction. Its direct callers are a baseline, each with the scope it sets and where
 *       that scope comes from. The baseline may only shrink.
 * </ul>
 *
 * A new caller of either is a deliberate change to its list, reviewed as such.
 *
 * <p>Detection (bytecode level): every call and method reference whose target is declared on, or
 * inherited by, the guarded class, from any code of the calling class including its lambdas. A call
 * made through reflection is not seen. Only DIRECT callers are checked: two baseline entries,
 * {@code ManagerCreator} and {@code TenantScopedJobRunner}, forward a tenant their own caller
 * supplies, and their callers are not followed. Opening a new transaction ({@code execute}, {@code
 * executeNew}, {@code forEachTenant}) is not a change of an open transaction's scope and is out of
 * these rules.
 */
final class WorkflowOwnerScopeCallerRules {

  static final String MOVE_METHOD = "executeOnCurrentTransaction";

  static final String SET_SCOPE_METHOD = "setScopeOnCurrentTransaction";

  /**
   * The classes allowed to call {@link TenantScopedTransaction#setScopeOnCurrentTransaction}, each
   * with the scope it sets and where it comes from. Every entry must still call it, and the list
   * may only shrink: its size is frozen in {@link WorkflowOwnerScopeCallerArchTest}.
   */
  static final Map<Class<?>, String> SCOPE_SETTERS =
      Map.of(
          ScenarioToExerciseService.class,
          "copies a scenario into a simulation in the scenario's own tenant, read from the scenario"
              + " it is given",
          AutonomousRunService.class,
          "narrows the create to one tenant: the seed scenario's, checked within the request"
              + " scope, or the tenant the request writes to",
          ConnectorInstanceService.class,
          "widens the XTM Composer reads to every tenant: one composer manages the connector"
              + " instances of every tenant",
          XtmHubService.class,
          "contactUs reads the default tenant's own registration, whatever the caller's tenant",
          OpenCTIConnectorPrivilegeService.class,
          "background (OpenCTI connector register and ping job): the connector's own tenant",
          QueueChainingJob.class,
          "background: moves its one transaction to each popped entry's tenant, read from the"
              + " entry's simulation or scenario",
          TenantScopedJobRunner.class,
          "runInCurrentTenantTransaction forwards a tenant its caller supplies; its callers are"
              + " not followed",
          ManagerCreator.class,
          "createManager re-asserts the tenant it is given on the transaction it joins; its"
              + " callers are not followed",
          MigrationProcessor.class,
          "tenant onboarding: joins the create-tenant transaction, scoped to the new tenant",
          InitAdminCommandLineRunner.class,
          "startup: provisions the default tenant");

  /**
   * The classes allowed to call {@link WorkflowOwnerScope}, each with how the simulation or
   * scenario it passes was loaded. Every entry must still call it: an entry with no call left is
   * removed in the same change.
   */
  static final Map<Class<?>, String> OWNER_SCOPE_CALLERS =
      Map.of(
          ExerciseApi.class,
          "the export passes the simulation from ExerciseService#exercise, findByIdAndTenantId on"
              + " the request's tenant",
          ExerciseService.class,
          "every call passes a simulation from ExerciseService#exercise (findByIdAndTenantId on the"
              + " request's tenant), or the duplicate copyExercise built from one, which carries"
              + " its tenant",
          ExportService.class,
          "the export is given the simulation ExerciseApi loaded through ExerciseService#exercise",
          ScenarioApi.class,
          "the launch passes the scenario from ScenarioService#scenario, findByIdAndTenantId on"
              + " the request's tenant",
          ScenarioService.class,
          "every call passes a scenario from ScenarioService#scenario (findByIdAndTenantId on the"
              + " request's tenant), or the duplicate copyScenario built from one, which carries"
              + " its tenant");

  private WorkflowOwnerScopeCallerRules() {}

  /** Only {@link WorkflowOwnerScope} moves the current transaction to another tenant. */
  static final ArchRule ONLY_OWNER_SCOPE_MOVES_THE_CURRENT_TRANSACTION =
      classes()
          .should(
              notAccessOutside(
                  "not call TenantScopedTransaction." + MOVE_METHOD + " outside WorkflowOwnerScope",
                  WorkflowOwnerScopeCallerRules::movesTheCurrentTransaction,
                  Set.of(WorkflowOwnerScope.class.getName())))
          .because(
              MOVE_METHOD
                  + " moves the request's transaction to a tenant it does not check; only"
                  + " WorkflowOwnerScope, whose callers are allowlisted, may call it");

  /**
   * {@link TenantScopedTransaction#setScopeOnCurrentTransaction} is called only from the baseline.
   */
  static ArchRule scopeSetOnCurrentTransactionOnlyFrom(Set<String> baseline) {
    return classes()
        .should(
            notAccessOutside(
                "not call TenantScopedTransaction." + SET_SCOPE_METHOD + " outside the baseline",
                WorkflowOwnerScopeCallerRules::setsTheScopeOfTheCurrentTransaction,
                baseline))
        .because(
            SET_SCOPE_METHOD
                + " sets the scope of the transaction it joins to a tenant it does not check");
  }

  /** The baseline as class names. */
  static Set<String> scopeSetterNames() {
    Set<String> names = new TreeSet<>();
    SCOPE_SETTERS.keySet().forEach(caller -> names.add(caller.getName()));
    return names;
  }

  /** The top-level classes among {@code classes} that call the scope setter. */
  static Set<String> scopeSetters(JavaClasses classes) {
    return callers(classes, WorkflowOwnerScopeCallerRules::setsTheScopeOfTheCurrentTransaction);
  }

  /** {@link WorkflowOwnerScope} is called only from the reasoned allowlist. */
  static ArchRule ownerScopeCalledOnlyFrom(Set<String> allowedCallers) {
    Set<String> allowed = new TreeSet<>(allowedCallers);
    allowed.add(WorkflowOwnerScope.class.getName());
    return classes()
        .should(
            notAccessOutside(
                "not call WorkflowOwnerScope outside the reasoned allowlist",
                WorkflowOwnerScopeCallerRules::reachesOwnerScope,
                allowed))
        .because(
            "WorkflowOwnerScope runs work in the tenant of the simulation or scenario it is given,"
                + " which is safe only for one loaded through the request's access check");
  }

  /** The allowlist as class names. */
  static Set<String> ownerScopeCallerNames() {
    Set<String> names = new TreeSet<>();
    OWNER_SCOPE_CALLERS.keySet().forEach(caller -> names.add(caller.getName()));
    return names;
  }

  /** The top-level classes among {@code classes} that call {@link WorkflowOwnerScope}. */
  static Set<String> ownerScopeCallers(JavaClasses classes) {
    Set<String> callers = callers(classes, WorkflowOwnerScopeCallerRules::reachesOwnerScope);
    callers.remove(WorkflowOwnerScope.class.getName());
    return callers;
  }

  private static Set<String> callers(
      JavaClasses classes, Predicate<JavaCodeUnitAccess<?>> guarded) {
    Set<String> callers = new TreeSet<>();
    for (JavaClass clazz : classes) {
      for (JavaCodeUnitAccess<?> access : clazz.getCodeUnitAccessesFromSelf()) {
        if (guarded.test(access)) {
          callers.add(topLevelName(access.getOriginOwner()));
        }
      }
    }
    return callers;
  }

  static boolean movesTheCurrentTransaction(JavaCodeUnitAccess<?> access) {
    return MOVE_METHOD.equals(access.getName())
        && access.getTargetOwner().isAssignableTo(TenantScopedTransaction.class);
  }

  static boolean setsTheScopeOfTheCurrentTransaction(JavaCodeUnitAccess<?> access) {
    return SET_SCOPE_METHOD.equals(access.getName())
        && access.getTargetOwner().isAssignableTo(TenantScopedTransaction.class);
  }

  static boolean reachesOwnerScope(JavaCodeUnitAccess<?> access) {
    return access.getTargetOwner().isAssignableTo(WorkflowOwnerScope.class);
  }

  // A lambda or a nested class belongs to the top-level class that declares it.
  private static String topLevelName(JavaClass clazz) {
    String name = clazz.getFullName();
    int nested = name.indexOf('$');
    return nested < 0 ? name : name.substring(0, nested);
  }

  private static ArchCondition<JavaClass> notAccessOutside(
      String description, Predicate<JavaCodeUnitAccess<?>> guarded, Set<String> allowed) {
    return new ArchCondition<>(description) {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        if (allowed.contains(topLevelName(clazz))) {
          return;
        }
        for (JavaCodeUnitAccess<?> access : clazz.getCodeUnitAccessesFromSelf()) {
          if (guarded.test(access)) {
            events.add(
                SimpleConditionEvent.violated(
                    access,
                    topLevelName(clazz) + " is not allowed to: " + access.getDescription()));
          }
        }
      }
    };
  }
}
