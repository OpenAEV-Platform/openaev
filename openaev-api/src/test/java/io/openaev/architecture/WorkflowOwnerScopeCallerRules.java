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
import io.openaev.rest.exercise.ExerciseApi;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.exercise.service.ExportService;
import io.openaev.rest.scenario.ScenarioApi;
import io.openaev.service.chaining.WorkflowOwnerScope;
import io.openaev.service.scenario.ScenarioService;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Who may move a transaction into another tenant than the one its request is scoped to.
 *
 * <p>{@link TenantScopedTransaction#executeOnCurrentTransaction} moves the current transaction to
 * one tenant, which may lie outside the request's scope: nothing in the method checks that the
 * caller may see that tenant. {@link WorkflowOwnerScope} is its only sanctioned caller, and passes
 * the tenant of a simulation or scenario. That is safe only when the simulation or scenario was
 * loaded through the request's own access check, so the callers of {@link WorkflowOwnerScope} are
 * an allowlist, each with the reason the owner it passes was loaded that way. A new caller is a
 * deliberate change to this list, reviewed as such.
 *
 * <p>Detection (bytecode level): every call and method reference whose target is declared on, or
 * inherited by, the guarded class, from any code of the calling class including its lambdas. A call
 * made through reflection is not seen.
 */
final class WorkflowOwnerScopeCallerRules {

  static final String MOVE_METHOD = "executeOnCurrentTransaction";

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
    Set<String> callers = new TreeSet<>();
    for (JavaClass clazz : classes) {
      for (JavaCodeUnitAccess<?> access : clazz.getCodeUnitAccessesFromSelf()) {
        String caller = topLevelName(access.getOriginOwner());
        if (reachesOwnerScope(access) && !caller.equals(WorkflowOwnerScope.class.getName())) {
          callers.add(caller);
        }
      }
    }
    return callers;
  }

  static boolean movesTheCurrentTransaction(JavaCodeUnitAccess<?> access) {
    return MOVE_METHOD.equals(access.getName())
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
