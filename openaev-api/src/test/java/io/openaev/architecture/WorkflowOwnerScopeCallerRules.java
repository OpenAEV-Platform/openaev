package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.aop.AccessControlAspect;
import io.openaev.config.security.SecurityService;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.integration.ManagerCreator;
import io.openaev.opencti.connectors.service.OpenCTIConnectorPrivilegeService;
import io.openaev.processor.MigrationProcessor;
import io.openaev.rest.exercise.ExerciseApi;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.exercise.service.ExportService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.runner.InitAdminCommandLineRunner;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.scheduler.jobs.QueueChainingJob;
import io.openaev.search.FullTextSearchService;
import io.openaev.service.GrantService;
import io.openaev.service.PermissionService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.account.CallerRightsInTenant;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.chaining.WorkflowOwnerScope;
import io.openaev.service.chaining.WorkflowUpdateEventPublisher;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.scenario.ScenarioLaunchService;
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
 *       tenant for the duration of some work. Its callers are an allowlist, each with the tenant it
 *       passes. {@link WorkflowOwnerScope} passes the tenant of a simulation, a scenario or an
 *       injector contract. That is safe only when the caller was granted that owner before passing
 *       it, so the callers of {@link WorkflowOwnerScope} are an allowlist, each with the check the
 *       owner it passes went through. {@link CallerRightsInTenant} passes the ambient tenant of the
 *       user it is given. Only the allowlist of {@link #CALLER_RIGHTS_LOADERS} calls it, and only
 *       the allowlist of {@link #AMBIENT_CALLER_READERS} calls {@link
 *       PermissionService#callerInAmbientTenant}, which passes it the user it is given. No rule
 *       checks that user: the callers of the {@link PermissionService} methods that pass on a
 *       caller are not followed.
 *   <li>{@link TenantScopedTransaction#setScopeOnCurrentTransaction} sets the scope for the rest of
 *       the transaction. Its direct callers are a baseline, each with the scope it sets and where
 *       that scope comes from. The baseline shrinks when a caller is converted off it, and grows
 *       only for a reviewed, deliberate new need to widen an ambient transaction the caller does
 *       not own (e.g. a security-filter callback with no request {@code TxCtx} of its own); the
 *       frozen size moves with it, in the same change.
 * </ul>
 *
 * A new caller of any of them is a deliberate change to its list, reviewed as such.
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
   * with the scope it sets and where it comes from. Every entry must still call it; its size is
   * frozen in {@link WorkflowOwnerScopeCallerArchTest} and moves only with a reviewed change here.
   */
  static final Map<Class<?>, String> SCOPE_SETTERS =
      Map.ofEntries(
          Map.entry(
              ScenarioToExerciseService.class,
              "copies a scenario into a simulation in the scenario's own tenant, read from the"
                  + " scenario it is given"),
          Map.entry(
              AutonomousRunService.class,
              "narrows the create to one tenant: the seed scenario's, checked within the request"
                  + " scope, or the tenant the request writes to"),
          Map.entry(
              ConnectorInstanceService.class,
              "widens the XTM Composer reads to every tenant: one composer manages the connector"
                  + " instances of every tenant"),
          Map.entry(
              XtmHubService.class,
              "contactUs reads the default tenant's own registration, whatever the caller's"
                  + " tenant"),
          Map.entry(
              OpenCTIConnectorPrivilegeService.class,
              "background (OpenCTI connector register and ping job): the connector's own tenant"),
          Map.entry(
              QueueChainingJob.class,
              "background: moves its one transaction to each popped entry's tenant, read from the"
                  + " entry's simulation or scenario"),
          Map.entry(
              TenantScopedJobRunner.class,
              "runInCurrentTenantTransaction forwards a tenant its caller supplies; its callers"
                  + " are not followed"),
          Map.entry(
              ManagerCreator.class,
              "createManager re-asserts the tenant it is given on the transaction it joins; its"
                  + " callers are not followed"),
          Map.entry(
              MigrationProcessor.class,
              "tenant onboarding: joins the create-tenant transaction, scoped to the new tenant"),
          Map.entry(InitAdminCommandLineRunner.class, "startup: provisions the default tenant"),
          Map.entry(
              SecurityService.class,
              "SSO login success handler: widens the group part of the login to the provider's"
                  + " tenant, the tenants a configured group mapping names, and the tenants the"
                  + " user already belongs to, read before any mapping runs, so a revoke sees the"
                  + " rows it must remove; the login has no request TxCtx of its own to carry the"
                  + " scope"));

  /**
   * The classes allowed to call {@link WorkflowOwnerScope}, each with how the simulation, scenario
   * or injector contract it passes was loaded. Every entry must still call it: an entry with no
   * call left is removed in the same change.
   *
   * <p>"The request scope" below is the tenant scope of the request's {@code TxCtx}, read by {@code
   * TxCtxScopeUtils#requestReadScope}: the path tenant on the {@code /api/tenants/{tenantId}}
   * route, the {@code X-Tenant-Ids} selector or the caller's memberships on the non-prefixed route,
   * and the ambient tenant when that scope is empty. "The ambient tenant" is the v1 thread-local
   * {@link io.openaev.context.TenantContext#getCurrentTenant()}: the path tenant on the {@code
   * /api/tenants/{tenantId}} route, which {@code TenantInterceptor} sets once it has checked the
   * caller's membership of it, and the default tenant on the non-prefixed route, which sets it from
   * neither the caller's scope nor an {@code X-Tenant-Ids} header.
   */
  static final Map<Class<?>, String> OWNER_SCOPE_CALLERS =
      Map.of(
          ExerciseApi.class,
          "the export's file name passes the simulation from ExerciseService#exercise(TxCtx,"
              + " String), findByIdAndTenantIdIn on the request scope",
          ExportService.class,
          "the simulation's export passes the simulation ExerciseApi loaded through"
              + " ExerciseService#exercise(TxCtx, String), findByIdAndTenantIdIn on the request"
              + " scope, and scopes only its workflow reads",
          ExerciseService.class,
          "every call passes a simulation from ExerciseService#exercise, findByIdAndTenantIdIn on"
              + " the request scope or, for work without a request, findByIdAndTenantId on the"
              + " ambient tenant, or the duplicate copyExercise built from one, which carries its"
              + " tenant",
          ScenarioLaunchService.class,
          "the launch passes the scenario from ScenarioService#scenario(TxCtx, String),"
              + " findByIdAndTenantIdIn on the request scope",
          ScenarioService.class,
          "every call passes a scenario from ScenarioService#scenario, findByIdAndTenantIdIn on"
              + " the request scope or, for work without a request, findByIdAndTenantId on the"
              + " ambient tenant, or the duplicate copyScenario built from one, which carries its"
              + " tenant",
          InjectorContractService.class,
          "the step sweep of a deleted contract passes the contract it deletes, loaded by"
              + " deleteInjectorContract(String) under the tenantFilter armed on the ambient"
              + " tenant, or by deleteInjectorContractById on the ambient tenant, after the DELETE"
              + " access check",
          WorkflowUpdateEventPublisher.class,
          "the update-event bridge passes the tenant of the inject or expectation that changed,"
              + " read from its own row, after the change itself was granted and written");

  /**
   * The classes allowed to call {@link TenantScopedTransaction#executeOnCurrentTransaction}, each
   * with the tenant it passes and what the work reads there. Every entry must still call it. The
   * rule checks the calling class, not the tenant: the callers of each entry are an allowlist of
   * their own ({@link #OWNER_SCOPE_CALLERS}, {@link #CALLER_RIGHTS_LOADERS}).
   */
  static final Map<Class<?>, String> TRANSACTION_MOVERS =
      Map.of(
          WorkflowOwnerScope.class,
          "passes the tenant of a simulation, a scenario or an injector contract its allowlisted"
              + " callers were granted",
          CallerRightsInTenant.class,
          "passes the ambient tenant of the user it is given, and loads that user there, in a"
              + " session of its own, with its eager associations; only the classes of"
              + " CALLER_RIGHTS_LOADERS call it",
          GrantService.class,
          "narrows the grant existence check to the tenant of the group it is already filtering"
              + " on (User#scopedTenantId, the group's own tenant or a platform group), so the"
              + " query's explicit tenant parameter and the scope it runs under always agree, for"
              + " a grant held through a group of a tenant the request's own scope does not hold");

  /** The method of {@link PermissionService} that reaches {@link CallerRightsInTenant}. */
  static final String AMBIENT_CALLER_METHOD = "callerInAmbientTenant";

  /**
   * The classes allowed to call {@link CallerRightsInTenant}, each with the user it passes. {@link
   * CallerRightsInTenant} itself is allowed too. Every entry must call it.
   */
  static final Map<Class<?>, String> CALLER_RIGHTS_LOADERS =
      Map.of(
          PermissionService.class,
          AMBIENT_CALLER_METHOD
              + " and withRolesInAmbientTenant pass the user they are given, whose rights or"
              + " groups in the ambient tenant the scope of the request does not hold");

  /**
   * The classes allowed to call {@link PermissionService#callerInAmbientTenant}, each with the user
   * it passes. Every entry must call it. The callers of the {@link PermissionService} methods that
   * call it are not followed.
   */
  static final Map<Class<?>, String> AMBIENT_CALLER_READERS =
      Map.of(
          AccessControlAspect.class,
          "the endpoint check passes the request's principal, read by"
              + " UserService#currentUser, and the request's TxCtx",
          PermissionService.class,
          "holdsInScope and decideInEachTenant pass the caller and the TxCtx they are given;"
              + " their own callers are not followed",
          FullTextSearchService.class,
          "the capability checks of the search pass the principal, read by"
              + " UserService#currentUser, and the request's TxCtx");

  private WorkflowOwnerScopeCallerRules() {}

  /** {@link CallerRightsInTenant} is called only from the reasoned allowlist. */
  static ArchRule callerRightsLoadedOnlyFrom(Set<String> allowedCallers) {
    Set<String> allowed = new TreeSet<>(allowedCallers);
    allowed.add(CallerRightsInTenant.class.getName());
    return classes()
        .should(
            notAccessOutside(
                "not call CallerRightsInTenant outside the reasoned allowlist",
                WorkflowOwnerScopeCallerRules::reachesCallerRights,
                allowed))
        .because(
            "CallerRightsInTenant moves the request's transaction to the ambient tenant to load"
                + " the user it is given, which it does not check");
  }

  /** The allowlist of {@link CallerRightsInTenant} as class names. */
  static Set<String> callerRightsLoaderNames() {
    return namesOf(CALLER_RIGHTS_LOADERS);
  }

  /** The top-level classes among {@code classes}, but itself, that call it. */
  static Set<String> callerRightsLoaders(JavaClasses classes) {
    Set<String> callers = callers(classes, WorkflowOwnerScopeCallerRules::reachesCallerRights);
    callers.remove(CallerRightsInTenant.class.getName());
    return callers;
  }

  /** {@link PermissionService#callerInAmbientTenant} is called only from the reasoned allowlist. */
  static ArchRule ambientCallerReadOnlyFrom(Set<String> allowedCallers) {
    Set<String> allowed = new TreeSet<>(allowedCallers);
    allowed.add(PermissionService.class.getName());
    return classes()
        .should(
            notAccessOutside(
                "not call PermissionService."
                    + AMBIENT_CALLER_METHOD
                    + " outside the reasoned allowlist",
                WorkflowOwnerScopeCallerRules::readsTheAmbientCaller,
                allowed))
        .because(
            AMBIENT_CALLER_METHOD
                + " loads the user it is given in the ambient tenant, outside the scope of the"
                + " request");
  }

  /** The allowlist of {@link PermissionService#callerInAmbientTenant} as class names. */
  static Set<String> ambientCallerReaderNames() {
    return namesOf(AMBIENT_CALLER_READERS);
  }

  /** The top-level classes among {@code classes} that call it. */
  static Set<String> ambientCallerReaders(JavaClasses classes) {
    return callers(classes, WorkflowOwnerScopeCallerRules::readsTheAmbientCaller);
  }

  private static Set<String> namesOf(Map<Class<?>, String> reasoned) {
    Set<String> names = new TreeSet<>();
    reasoned.keySet().forEach(caller -> names.add(caller.getName()));
    return names;
  }

  /** Only the classes of {@link #TRANSACTION_MOVERS} move the current transaction. */
  static final ArchRule ONLY_LISTED_CLASSES_MOVE_THE_CURRENT_TRANSACTION =
      classes()
          .should(
              notAccessOutside(
                  "not call TenantScopedTransaction." + MOVE_METHOD + " outside the allowlist",
                  WorkflowOwnerScopeCallerRules::movesTheCurrentTransaction,
                  transactionMoverNames()))
          .because(
              MOVE_METHOD
                  + " moves the request's transaction to a tenant it does not check; only the"
                  + " allowlisted classes, each with the tenant it passes, may call it");

  /** The allowlist of the move as class names. */
  static Set<String> transactionMoverNames() {
    Set<String> names = new TreeSet<>();
    TRANSACTION_MOVERS.keySet().forEach(mover -> names.add(mover.getName()));
    return names;
  }

  /** The top-level classes among {@code classes} that move the current transaction. */
  static Set<String> transactionMovers(JavaClasses classes) {
    return callers(classes, WorkflowOwnerScopeCallerRules::movesTheCurrentTransaction);
  }

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
            "WorkflowOwnerScope runs work in the tenant of the owner it is given,"
                + " which is safe only for one the caller has already been granted");
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

  static boolean reachesCallerRights(JavaCodeUnitAccess<?> access) {
    return access.getTargetOwner().isAssignableTo(CallerRightsInTenant.class);
  }

  static boolean readsTheAmbientCaller(JavaCodeUnitAccess<?> access) {
    return AMBIENT_CALLER_METHOD.equals(access.getName())
        && access.getTargetOwner().isAssignableTo(PermissionService.class);
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
