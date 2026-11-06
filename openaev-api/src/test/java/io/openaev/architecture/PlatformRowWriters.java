package io.openaev.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.aop.PlatformWrite;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.repository.SettingRepository;
import java.util.HashSet;
import java.util.Set;

/**
 * What the guard on the writers of {@code parameters} platform rows checks, and how. A platform row
 * ({@code tenant_id IS NULL}) is readable under any scope but written only inside a platform-write
 * transaction, so the guard follows the WRITES: a method that writes through {@link
 * SettingRepository} must be reached, on every path the imported classes show, through a method
 * that carries {@link PlatformWrite} or that opens a platform-write transaction ({@link
 * TenantScopedTransaction} {@code executeAsPlatformWrite}, {@code executeNewAsPlatformWrite} or
 * {@code setPlatformWriteOnCurrentTransaction}).
 *
 * <p>Denominator: the callers ArchUnit resolves in the imported classes. A call through an
 * interface, a reflective call and a Spring event dispatch are not followed, so a writer reached
 * only that way has no caller and is reported unless it is itself marked; that shape fails closed.
 * A lambda body is a method of its own class: it counts as marked when any method of that class
 * opens a platform-write transaction.
 */
final class PlatformRowWriters {

  private static final Set<String> MARKING_CALLS =
      Set.of(
          "executeAsPlatformWrite",
          "executeNewAsPlatformWrite",
          "setPlatformWriteOnCurrentTransaction");

  private PlatformRowWriters() {}

  /** Methods that save or delete through the settings repository, outside {@code tenantRowOnly}. */
  static DescribedPredicate<JavaMethod> writeThroughTheSettingsRepository(
      Set<Class<?>> tenantRowOnly) {
    return new DescribedPredicate<>("write through the settings repository") {
      @Override
      public boolean test(JavaMethod method) {
        if (tenantRowOnly.stream().anyMatch(c -> method.getOwner().isAssignableTo(c))) {
          return false;
        }
        return method.getMethodCallsFromSelf().stream()
            .anyMatch(
                call ->
                    call.getTargetOwner().isAssignableTo(SettingRepository.class)
                        && isWrite(call.getName()));
      }
    };
  }

  private static boolean isWrite(String name) {
    return name.startsWith("save") || name.startsWith("delete");
  }

  /** Every path that reaches the method crosses a platform-write marker. */
  static ArchCondition<JavaMethod> beReachedOnlyUnderPlatformWrite() {
    return new ArchCondition<>("be reached only under a platform-write marker") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        Set<JavaCodeUnit> unmarkedRoots = new HashSet<>();
        collectUnmarkedRoots(method, new HashSet<>(), unmarkedRoots);
        for (JavaCodeUnit root : unmarkedRoots) {
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName()
                      + " writes a platform row of parameters and is reached from "
                      + root.getFullName()
                      + ", which is not under a platform-write marker"));
        }
      }
    };
  }

  private static void collectUnmarkedRoots(
      JavaCodeUnit unit, Set<JavaCodeUnit> visited, Set<JavaCodeUnit> unmarkedRoots) {
    if (!visited.add(unit) || isMarked(unit)) {
      return;
    }
    Set<JavaCodeUnit> callers = callersOf(unit);
    if (callers.isEmpty()) {
      unmarkedRoots.add(unit);
      return;
    }
    for (JavaCodeUnit caller : callers) {
      collectUnmarkedRoots(caller, visited, unmarkedRoots);
    }
  }

  private static Set<JavaCodeUnit> callersOf(JavaCodeUnit unit) {
    Set<JavaCodeUnit> callers = new HashSet<>();
    for (JavaAccess<?> access : unit.getAccessesToSelf()) {
      callers.add(access.getOrigin());
    }
    return callers;
  }

  private static boolean isMarked(JavaCodeUnit unit) {
    if (unit.isAnnotatedWith(PlatformWrite.class)
        || unit.getOwner().isAnnotatedWith(PlatformWrite.class)) {
      return true;
    }
    if (opensPlatformWrite(unit)) {
      return true;
    }
    // A lambda body is reached through the method that built it, which ArchUnit does not link to
    // it: it is marked when a method of its class opens the platform-write transaction.
    return unit.getName().startsWith("lambda$") && anyMethodOpensPlatformWrite(unit.getOwner());
  }

  private static boolean anyMethodOpensPlatformWrite(JavaClass owner) {
    return owner.getCodeUnits().stream().anyMatch(PlatformRowWriters::opensPlatformWrite);
  }

  private static boolean opensPlatformWrite(JavaCodeUnit unit) {
    return unit.getMethodCallsFromSelf().stream()
        .anyMatch(
            call ->
                call.getTargetOwner().isAssignableTo(TenantScopedTransaction.class)
                    && MARKING_CALLS.contains(call.getName()));
  }
}
