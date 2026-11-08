package io.openaev.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.database.model.Inject;
import io.openaev.utils.InjectUtils;
import java.util.Set;

/**
 * An inject is written in the tenant of its owner. Left to {@code TenantBaseListener}, an inject
 * takes the ambient tenant, which the non-prefixed route leaves on the default one whatever tenant
 * its parent simulation or scenario belongs to: the caller of that tenant would then not find its
 * own inject by id.
 *
 * <p>The rule looks at the two places where an inject is born or given to its owner: a code unit
 * that instantiates an {@link Inject}, and one that calls {@code Inject#setScenario} or {@code
 * Inject#setExercise}. Each must also call {@code Inject#setTenant}, directly or through {@code
 * InjectUtils#attachToScenario} / {@code #attachToExercise}, which do. A code unit that has a
 * reason not to (an inject that is never persisted, a builder whose caller attaches the result to
 * its owner) is named in {@link #EXEMPT} with that reason.
 *
 * <p>What it does not see: an inject built by a factory ({@code InjectInput#toInject}, {@code
 * InjectService#buildInject}) and saved without ever being given an owner; an inject whose owner is
 * set by reflection or by a JSON mapper; a call through an interface. The behavioural tests of the
 * creation paths cover the paths named in {@code InjectCreateTenantTest}.
 */
final class InjectCreationTenantRules {

  /** The code units allowed to instantiate or attach an inject without stamping its tenant. */
  static final Set<String> EXEMPT =
      Set.of(
          // Builders: the caller gives the inject to its owner, through InjectUtils#attachTo*.
          "InjectInput#toInject",
          "DirectInjectInput#toInject",
          "InjectService#buildInject",
          // An inject assembled only to build an executable message, never persisted.
          "MailingService#<any>",
          "ComchecksExecutionJob#<any>",
          // A search result row shaped as an inject, never persisted.
          "InjectSearchService#<any>");

  private InjectCreationTenantRules() {}

  static DescribedPredicate<JavaCodeUnit> createOrAttachAnInject() {
    return new DescribedPredicate<>("instantiate an Inject or give it a scenario or a simulation") {
      @Override
      public boolean test(JavaCodeUnit unit) {
        return unit.getCallsFromSelf().stream().anyMatch(InjectCreationTenantRules::isBirthOrOwner)
            && !isExempt(unit);
      }
    };
  }

  static ArchCondition<JavaCodeUnit> alsoStampTheTenant() {
    return new ArchCondition<>("call Inject#setTenant, or InjectUtils#attachToScenario/Exercise") {
      @Override
      public void check(JavaCodeUnit unit, ConditionEvents events) {
        boolean stamps =
            unit.getCallsFromSelf().stream()
                .anyMatch(
                    call ->
                        isInjectCall(call, "setTenant")
                            || isInjectUtilsCall(call, "attachToScenario")
                            || isInjectUtilsCall(call, "attachToExercise"));
        if (!stamps) {
          events.add(
              SimpleConditionEvent.violated(
                  unit,
                  unit.getOwner().getSimpleName()
                      + "#"
                      + unit.getName()
                      + " creates an inject or gives it an owner and leaves its tenant to the"
                      + " listener, which stamps the ambient tenant ("
                      + unit.getSourceCodeLocation()
                      + ")"));
        }
      }
    };
  }

  private static boolean isBirthOrOwner(JavaCall<?> call) {
    return isInjectCall(call, "<init>")
        || isInjectCall(call, "setScenario")
        || isInjectCall(call, "setExercise");
  }

  private static boolean isInjectCall(JavaCall<?> call, String name) {
    return call.getTargetOwner().isEquivalentTo(Inject.class) && call.getName().equals(name);
  }

  private static boolean isInjectUtilsCall(JavaCall<?> call, String name) {
    return call.getTargetOwner().isEquivalentTo(InjectUtils.class) && call.getName().equals(name);
  }

  private static boolean isExempt(JavaCodeUnit unit) {
    String owner = unit.getOwner().getSimpleName();
    String outermost =
        unit.getOwner().getEnclosingClass().map(JavaClass::getSimpleName).orElse(owner);
    return EXEMPT.contains(owner + "#" + unit.getName())
        || EXEMPT.contains(owner + "#<any>")
        || EXEMPT.contains(outermost + "#<any>");
  }
}
