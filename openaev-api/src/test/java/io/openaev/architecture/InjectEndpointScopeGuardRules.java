package io.openaev.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.PermissionService;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The by-id scope guard rule: an endpoint whose {@link AccessControl} names an inject (or an atomic
 * testing, which is an inject) as its resource must load that inject inside the request's tenant
 * scope. {@code PermissionService#checkPermission} lets an admin or a BYPASS caller through without
 * resolving the resource's parent, and a primary-key load is not narrowed by the v1 tenant filter,
 * so the guard is the only tenant check those callers meet.
 *
 * <p>A guard is one of the three scoped lookups of {@link InjectService}: {@code inject(TxCtx,
 * String)}, {@code findInScope} and {@code existsInScope}. Every other guard of the controllers
 * ({@code requireInjectInScope}, {@code requireAtomicTestingInScope}, {@code
 * requireInjectInScenario}, {@code requireInjectInSimulation}, {@code
 * AtomicTestingService#existsInScope}) ends in one of them, so reaching one of them, directly or
 * through the methods the endpoint delegates to, is what the rule asks for.
 *
 * <p>Denominator: an endpoint is a method with an HTTP mapping and an {@code @AccessControl} whose
 * {@code resourceType} is INJECT, ATOMIC_TESTING or INJECT_SECRET and whose {@code resourceId} is
 * set. An endpoint with no {@code resourceId} (a search over a collection) names no inject to
 * guard. An endpoint typed with another resource that loads an inject by id (a scenario's inject,
 * an implant's JOB callback) is not seen. Reachability follows method calls and method references,
 * the lambdas of the method it expands, and stops at {@link PermissionService} (which loads the
 * inject for the aspect, not for the handler) and at {@link #MAX_CALL_DEPTH} calls, so an unrelated
 * call far down a service does not count as the endpoint's guard. A call through an interface or a
 * reflective call is not followed.
 */
final class InjectEndpointScopeGuardRules {

  private static final Set<ResourceType> INJECT_TYPES =
      Set.of(ResourceType.INJECT, ResourceType.ATOMIC_TESTING, ResourceType.INJECT_SECRET);

  private static final Set<String> GUARD_NAMES = Set.of("inject", "findInScope", "existsInScope");

  /**
   * Controller, guard helper, owning service, {@code InjectService}: the deepest chain of the
   * controllers today, with one call of slack.
   */
  static final int MAX_CALL_DEPTH = 4;

  private InjectEndpointScopeGuardRules() {}

  static DescribedPredicate<JavaMethod> byIdInjectEndpoints() {
    return new DescribedPredicate<>(
        "are by-id endpoints typed INJECT, ATOMIC_TESTING or INJECT_SECRET") {
      @Override
      public boolean test(JavaMethod method) {
        if (!isHttpEndpoint(method) || !method.isAnnotatedWith(AccessControl.class)) {
          return false;
        }
        AccessControl accessControl = method.getAnnotationOfType(AccessControl.class);
        return INJECT_TYPES.contains(accessControl.resourceType())
            && !accessControl.resourceId().isBlank();
      }
    };
  }

  static ArchCondition<JavaMethod> reachAScopeGuard() {
    return new ArchCondition<>(
        "reach InjectService#inject(TxCtx, String), #findInScope or #existsInScope within "
            + MAX_CALL_DEPTH
            + " calls") {
      @Override
      public void check(JavaMethod endpoint, ConditionEvents events) {
        if (!reachesAScopeGuard(endpoint)) {
          events.add(
              SimpleConditionEvent.violated(
                  endpoint,
                  endpoint.getOwner().getSimpleName()
                      + "#"
                      + endpoint.getName()
                      + " names an inject as its resource and never reaches a scope guard: an"
                      + " admin or BYPASS caller meets no tenant check on it ("
                      + endpoint.getSourceCodeLocation()
                      + ")"));
        }
      }
    };
  }

  static boolean reachesAScopeGuard(JavaMethod endpoint) {
    Set<JavaCodeUnit> seen = new HashSet<>();
    Deque<Expansion> pending = new ArrayDeque<>();
    pending.add(new Expansion(endpoint, 0));
    seen.add(endpoint);
    while (!pending.isEmpty()) {
      Expansion current = pending.poll();
      for (JavaMethod target : calledMethods(current.method())) {
        if (isScopeGuard(target)) {
          return true;
        }
        if (current.depth() + 1 < MAX_CALL_DEPTH && followed(target) && seen.add(target)) {
          pending.add(new Expansion(target, current.depth() + 1));
        }
      }
    }
    return false;
  }

  private record Expansion(JavaMethod method, int depth) {}

  private static boolean isScopeGuard(JavaMethod target) {
    return target.getOwner().isEquivalentTo(InjectService.class)
        && GUARD_NAMES.contains(target.getName())
        && !target.getRawParameterTypes().isEmpty()
        && target.getRawParameterTypes().get(0).isEquivalentTo(TxCtx.class);
  }

  private static boolean followed(JavaMethod target) {
    return target.getOwner().getPackageName().startsWith("io.openaev")
        && !target.getOwner().isEquivalentTo(PermissionService.class);
  }

  /** The methods a method calls or references, its own lambdas included. */
  private static Set<JavaMethod> calledMethods(JavaMethod method) {
    Set<JavaMethod> called = new HashSet<>();
    Stream.concat(Stream.of(method), lambdasOf(method))
        .flatMap(unit -> unit.getAccessesFromSelf().stream())
        .map(InjectEndpointScopeGuardRules::targetMethod)
        .flatMap(Optional::stream)
        .forEach(called::add);
    return called;
  }

  private static Stream<JavaMethod> lambdasOf(JavaMethod method) {
    String prefix = "lambda$" + method.getName() + "$";
    return method.getOwner().getMethods().stream()
        .filter(candidate -> candidate.getName().startsWith(prefix));
  }

  private static Optional<JavaMethod> targetMethod(JavaAccess<?> access) {
    if (access instanceof JavaMethodCall call) {
      return call.getTarget().resolveMember();
    }
    if (access instanceof JavaMethodReference reference) {
      return reference.getTarget().resolveMember();
    }
    return Optional.empty();
  }

  private static boolean isHttpEndpoint(JavaMethod method) {
    return method.isAnnotatedWith(GetMapping.class)
        || method.isAnnotatedWith(PostMapping.class)
        || method.isAnnotatedWith(PutMapping.class)
        || method.isAnnotatedWith(DeleteMapping.class)
        || method.isAnnotatedWith(PatchMapping.class)
        || method.isAnnotatedWith(RequestMapping.class);
  }
}
