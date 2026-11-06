package io.openaev.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.aop.PlatformWrite;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.data.jpa.repository.Modifying;

/**
 * What the guards on {@code parameters} check, and how. A platform row ({@code tenant_id IS NULL})
 * is readable under any scope but written only inside a platform-write transaction; a tenant row
 * read with no scope comes back empty. So the guards follow the WRITES of a {@code Setting} and the
 * READS of one: every path that reaches one must cross a marker.
 *
 * <p>A writer is a method that modifies a {@code Setting}: a modifying method of {@link
 * SettingRepository} (a {@code @Modifying} query, or a name starting {@code save}, {@code delete},
 * {@code remove}, {@code update}, {@code insert}, {@code upsert}, which takes in every inherited
 * {@code CrudRepository} write), or an {@code EntityManager} {@code persist}/{@code merge}/{@code
 * remove} in a method that names {@code Setting} in its signature or touches one. A reader is any
 * repository {@code find*}/{@code exists*}/{@code count*} not named for the platform row.
 *
 * <p>A call is under a marker when the transaction it runs in is marked at that point:
 *
 * <ul>
 *   <li>the calling method is one the aspect honours: {@code @PlatformWrite} (platform write) or
 *       {@code @Transactional} (scope), on a method taking a {@code TxCtx};
 *   <li>the calling method calls the current-transaction setter ({@code
 *       setPlatformWriteOnCurrentTransaction}, {@code setScopeOnCurrentTransaction}) on a line
 *       before the call;
 *   <li>the call is made only from a lambda of the calling method, and the calling method itself
 *       calls the primitive that opens a marked transaction around a callback. A method that opens
 *       such a transaction and then calls directly, after the primitive returned, is NOT marked:
 *       only the callback is. ArchUnit folds a lambda's calls into its creator, so {@link
 *       CallSites} reads the class file to tell the two apart.
 * </ul>
 *
 * <p>Denominator and limits: the callers ArchUnit resolves in the imported classes. A call through
 * an interface, a reflective call and a Spring event dispatch are not followed, so a method reached
 * only that way has no caller and is reported unless it is itself marked (fails closed). A lambda
 * is tied to the primitive by its creator, not by the argument the primitive receives: a method
 * that creates two lambdas and hands only one to the primitive marks both.
 */
final class PlatformRowWriters {

  /** A method that writes a tenant row of {@code parameters} only, with the reason. */
  record TenantRowWriter(Class<?> owner, String method, String reason) {}

  /**
   * The writers that never touch a platform row: each one attributes the row to a tenant it names,
   * and the database checks that tenant against the transaction's scope. A write of a tenant row
   * does not need the marker. Each entry must stay a live writer ({@code
   * TenantActiveTableAccessArchTest}).
   */
  static final List<TenantRowWriter> TENANT_ROW_WRITERS =
      List.of(
          new TenantRowWriter(
              io.openaev.service.settings.TenantSettingsService.class,
              "clearSettingIfMatch",
              "clears a setting looked up by (key, tenantId): a tenant row"),
          new TenantRowWriter(
              io.openaev.service.settings.TenantSettingsService.class,
              "upsert",
              "creates or updates the tenant's own setting, the new row carries the tenant"),
          new TenantRowWriter(
              io.openaev.service.autonomous.AutonomousRunService.class,
              "updateDefaultAdditionalAgentIds",
              "writes the tenant's default agents, the tenant is the one the scope pins"),
          new TenantRowWriter(
              io.openaev.service.autonomous.AutonomousRunService.class,
              "updateDefaultAdditionalAgentModes",
              "writes the tenant's default agent modes, the tenant is the one the scope pins"),
          new TenantRowWriter(
              io.openaev.processor.datapack.V20260101_Starter_pack.class,
              "setDefaultDashboard",
              "sets the default dashboard of the tenant the starter pack is installed for"));

  /**
   * A root of a path to a tenant-row read that sets no scope itself, because the scope is set by a
   * caller ArchUnit cannot follow (a call through an abstract method). Each entry names the caller.
   */
  record ReviewedRoot(Class<?> owner, String method, String reason) {}

  static final List<ReviewedRoot> REVIEWED_UNSCOPED_ROOTS =
      List.of(
          new ReviewedRoot(
              io.openaev.processor.datapack.V20260101_Starter_pack.class,
              "doProcess",
              "called through DataPack#process by MigrationProcessor#init, which joins the"
                  + " onboarding transaction with setScopeOnCurrentTransaction or runs each pack in"
                  + " tenantTx.execute of the tenant's own scope"));

  /**
   * What marks a transaction: a method the aspect honours, a call on the current transaction, and
   * the primitives that open a marked transaction around a callback.
   */
  record Marker(
      Predicate<JavaMethod> honouredAnnotation,
      Set<String> currentTransactionCalls,
      Set<String> callbackPrimitives,
      String description) {}

  static final Marker PLATFORM_WRITE =
      new Marker(
          PlatformRowWriters::honoursPlatformWrite,
          Set.of("setPlatformWriteOnCurrentTransaction"),
          Set.of("executeAsPlatformWrite", "executeNewAsPlatformWrite"),
          "platform-write marker");

  /** The scope of a transaction: the aspect writes it from a TxCtx argument, the primitive too. */
  static final Marker TENANT_SCOPE =
      new Marker(
          PlatformRowWriters::hasTransactionalWithTxCtx,
          Set.of("setScopeOnCurrentTransaction"),
          Set.of(
              "execute",
              "executeNew",
              "executeAsPlatformWrite",
              "executeNewAsPlatformWrite",
              "forEachTenant"),
          "tenant scope");

  /** A call a unit makes toward the code under guard: its target and its line. */
  private record Edge(CallSites.Target target, int line) {}

  private static final List<String> WRITE_PREFIXES =
      List.of("save", "delete", "remove", "update", "insert", "upsert");

  private static final Set<String> ENTITY_MANAGER_WRITES = Set.of("persist", "merge", "remove");

  private static final Set<String> CALLBACK_TYPES =
      Set.of(
          "java.lang.Runnable",
          "java.util.function.Supplier",
          "java.util.function.Consumer",
          "java.util.function.Function",
          "java.util.concurrent.Callable");

  private static final Set<String> TRANSACTIONAL =
      Set.of(
          "org.springframework.transaction.annotation.Transactional",
          "jakarta.transaction.Transactional");

  private PlatformRowWriters() {}

  /** Methods that modify a {@code Setting}, outside {@code tenantRowWriters}. */
  static DescribedPredicate<JavaMethod> writeASetting(List<TenantRowWriter> tenantRowWriters) {
    return new DescribedPredicate<>("write a Setting") {
      @Override
      public boolean test(JavaMethod method) {
        return !isBridge(method)
            && !isTenantRowWriter(method, tenantRowWriters)
            && !writesOf(method).isEmpty();
      }
    };
  }

  /** Methods that read a tenant row of {@code parameters}. */
  static DescribedPredicate<JavaMethod> readATenantRowOfSettings() {
    return new DescribedPredicate<>("read a tenant row of parameters") {
      @Override
      public boolean test(JavaMethod method) {
        return !isBridge(method) && !readsOf(method).isEmpty();
      }
    };
  }

  /** The compiler's bridge to a covariant override repeats the call of the real method. */
  private static boolean isBridge(JavaMethod method) {
    return method.getModifiers().contains(JavaModifier.BRIDGE);
  }

  /** Whether {@code unit} is one of the listed tenant-row-only writers. */
  static boolean isTenantRowWriter(JavaCodeUnit unit, List<TenantRowWriter> tenantRowWriters) {
    return tenantRowWriters.stream()
        .anyMatch(
            e -> unit.getOwner().isAssignableTo(e.owner()) && e.method().equals(unit.getName()));
  }

  private static boolean isReviewedRoot(JavaCodeUnit unit) {
    return REVIEWED_UNSCOPED_ROOTS.stream()
        .anyMatch(
            r -> unit.getOwner().isAssignableTo(r.owner()) && r.method().equals(unit.getName()));
  }

  /** True when the method modifies a Setting, ignoring any exemption. */
  static boolean writesASetting(JavaMethod method) {
    return !writesOf(method).isEmpty();
  }

  private static List<JavaMethodCall> writesOf(JavaCodeUnit unit) {
    boolean mentionsSetting = mentionsSetting(unit);
    return unit.getMethodCallsFromSelf().stream()
        .filter(
            call ->
                (call.getTargetOwner().isAssignableTo(SettingRepository.class) && modifies(call))
                    || (mentionsSetting
                        && call.getTargetOwner().isAssignableTo(EntityManager.class)
                        && ENTITY_MANAGER_WRITES.contains(call.getName())))
        .toList();
  }

  private static List<JavaMethodCall> readsOf(JavaCodeUnit unit) {
    return unit.getMethodCallsFromSelf().stream()
        .filter(
            call ->
                call.getTargetOwner().isAssignableTo(SettingRepository.class)
                    && isTenantRowRead(call.getName()))
        .toList();
  }

  /** Every find, exists or count, but the ones named for the platform row. */
  private static boolean isTenantRowRead(String name) {
    boolean read = name.startsWith("find") || name.startsWith("exists") || name.startsWith("count");
    return read && !name.contains("TenantIsNull");
  }

  private static boolean modifies(JavaMethodCall call) {
    return WRITE_PREFIXES.stream().anyMatch(call.getName()::startsWith)
        || call.getTarget().resolveMember().stream()
            .anyMatch(member -> member.isAnnotatedWith(Modifying.class));
  }

  private static boolean mentionsSetting(JavaCodeUnit unit) {
    return unit.getRawParameterTypes().stream().anyMatch(t -> t.isAssignableTo(Setting.class))
        || unit.getAccessesFromSelf().stream()
            .anyMatch(a -> a.getTargetOwner().isAssignableTo(Setting.class));
  }

  /** Every path that reaches a platform-row write crosses a platform-write marker. */
  static ArchCondition<JavaMethod> beReachedOnlyUnderPlatformWrite() {
    return beReachedOnlyUnder(
        PLATFORM_WRITE, PlatformRowWriters::writesOf, "writes a platform row of parameters");
  }

  /** Every path to a reader crosses a tenant scope: an unscoped tenant row comes back empty. */
  static ArchCondition<JavaMethod> beReachedOnlyUnderATenantScope() {
    return beReachedOnlyUnder(
        TENANT_SCOPE, PlatformRowWriters::readsOf, "reads a tenant row of parameters");
  }

  private static ArchCondition<JavaMethod> beReachedOnlyUnder(
      Marker marker, Function<JavaCodeUnit, List<JavaMethodCall>> calls, String what) {
    return new ArchCondition<>("be reached only under the " + marker.description()) {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        Set<JavaCodeUnit> unmarkedRoots = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (JavaMethodCall call : calls.apply(method)) {
          Edge edge =
              new Edge(
                  new CallSites.Target(call.getTargetOwner().getName(), call.getName()),
                  call.getLineNumber());
          collectUnmarkedRoots(method, edge, marker, visited, unmarkedRoots);
        }
        for (JavaCodeUnit root : unmarkedRoots) {
          if (marker == TENANT_SCOPE && isReviewedRoot(root)) {
            continue;
          }
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName()
                      + " "
                      + what
                      + " and is reached from "
                      + root.getFullName()
                      + ", which is not under the "
                      + marker.description()));
        }
      }
    };
  }

  /**
   * Walks to the callers of {@code unit}; {@code edge} is the call {@code unit} makes toward the
   * guarded code, which is what a marker is compared against.
   */
  private static void collectUnmarkedRoots(
      JavaCodeUnit unit,
      Edge edge,
      Marker marker,
      Set<String> visited,
      Set<JavaCodeUnit> unmarkedRoots) {
    // The line is part of the key: two calls to one target, one before and one after a marker,
    // are two different questions.
    if (!visited.add(unit.getFullName() + "->" + edge) || isMarked(unit, edge, marker)) {
      return;
    }
    Set<JavaAccess<?>> callers = new HashSet<>(unit.getAccessesToSelf());
    if (callers.isEmpty()) {
      unmarkedRoots.add(unit);
      return;
    }
    for (JavaAccess<?> access : callers) {
      Edge toUnit =
          new Edge(
              new CallSites.Target(access.getTargetOwner().getName(), access.getName()),
              access.getLineNumber());
      collectUnmarkedRoots(access.getOrigin(), toUnit, marker, visited, unmarkedRoots);
    }
  }

  private static boolean isMarked(JavaCodeUnit unit, Edge edge, Marker marker) {
    if (unit instanceof JavaMethod method && marker.honouredAnnotation().test(method)) {
      return true;
    }
    if (edge.line() > 0
        && unit.getMethodCallsFromSelf().stream()
            .anyMatch(
                call ->
                    call.getTargetOwner().isAssignableTo(TenantScopedTransaction.class)
                        && marker.currentTransactionCalls().contains(call.getName())
                        && call.getLineNumber() > 0
                        && call.getLineNumber() < edge.line())) {
      return true;
    }
    // Inside a callback handed to a primitive that opens the marked transaction: the call is made
    // only from a lambda of the method, and the method itself calls such a primitive, or a helper
    // that runs its Runnable/Supplier parameter inside one.
    return CallSites.onlyInsideLambdas(unit.getOwner(), unit.getName(), edge.target())
        && opensCallbackTransaction(unit, marker, 0);
  }

  private static boolean opensCallbackTransaction(JavaCodeUnit unit, Marker marker, int depth) {
    for (JavaMethodCall call : unit.getMethodCallsFromSelf()) {
      CallSites.Target target =
          new CallSites.Target(call.getTargetOwner().getName(), call.getName());
      if (!CallSites.directlyCalls(unit.getOwner(), unit.getName(), target)) {
        continue;
      }
      if (call.getTargetOwner().isAssignableTo(TenantScopedTransaction.class)
          && marker.callbackPrimitives().contains(call.getName())) {
        return true;
      }
      if (depth < 3
          && call.getTarget().resolveMember().stream()
              .anyMatch(
                  helper ->
                      takesACallback(helper)
                          && opensCallbackTransaction(helper, marker, depth + 1))) {
        return true;
      }
    }
    return false;
  }

  private static boolean takesACallback(JavaCodeUnit unit) {
    return unit.getRawParameterTypes().stream().anyMatch(t -> CALLBACK_TYPES.contains(t.getName()));
  }

  /**
   * The aspect sets the marker only on a method directly annotated {@code @Transactional} that
   * takes a {@code TxCtx}; on any other method {@link PlatformWrite} does nothing.
   */
  static boolean honoursPlatformWrite(JavaMethod method) {
    return method.isAnnotatedWith(PlatformWrite.class) && hasTransactionalWithTxCtx(method);
  }

  /** The aspect writes the scope only for a directly annotated {@code @Transactional} method. */
  static boolean hasTransactionalWithTxCtx(JavaMethod method) {
    return TRANSACTIONAL.stream().anyMatch(method::isAnnotatedWith)
        && method.getRawParameterTypes().stream().anyMatch(t -> t.isAssignableTo(TxCtx.class));
  }

  /** {@code @PlatformWrite} on a method the aspect would ignore. */
  static ArchCondition<JavaMethod> beHonouredByTheAspect() {
    return new ArchCondition<>("be a @Transactional method with a TxCtx parameter") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (!honoursPlatformWrite(method)) {
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName()
                      + " carries @PlatformWrite but is not a @Transactional method with a TxCtx"
                      + " parameter: the aspect never sets the marker for it"));
        }
      }
    };
  }
}
