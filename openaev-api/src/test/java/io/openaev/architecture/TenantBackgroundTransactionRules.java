package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.context.TenantScopedTransaction;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.hibernate.SharedSessionContract;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The build-time guards behind the background transaction decision (#6398, widened #6391), shared
 * between the frozen-baseline arch test (production classes) and the fixture test that proves each
 * rule fires.
 *
 * <p>Rule descriptions are part of the freeze-store keys: keep them stable.
 */
final class TenantBackgroundTransactionRules {

  static final String JOBS_PACKAGE = "io.openaev.scheduler.jobs..";

  private TenantBackgroundTransactionRules() {}

  /**
   * The self-invocation trap, codebase-wide: an intra-class call to a {@code @Transactional} method
   * bypasses the Spring proxy, so neither the transaction nor the tenant scope is set, silently.
   *
   * <p>Known limits (bytecode-level detection): method references ({@code this::txMethod}) and
   * {@code super} calls are not seen; only method-level {@code @Transactional} is checked (not
   * class-level, whose callers already run behind the proxy, nor meta-annotations). The standard
   * self-injection workaround is flagged too, deliberately: the primitive is the way out. The
   * frozen baseline is therefore a floor, not an exact count.
   */
  static final ArchRule NO_TRANSACTIONAL_SELF_INVOCATION =
      classes()
          .should(notSelfInvokeATransactionalMethod())
          .because(
              "an intra-class call to a @Transactional method bypasses the Spring proxy:"
                  + " no transaction, no tenant scope, silently");

  /**
   * Background jobs open transactions through the primitive, never through @Transactional. Known
   * limit: only methods DECLARED on the job class are checked; {@code @Transactional} methods
   * inherited from a base class outside the package are not seen (same bytecode-level family of
   * limits as the self-invocation rule).
   */
  static final ArchRule NO_TRANSACTIONAL_IN_JOBS =
      classes()
          .that()
          .resideInAPackage(JOBS_PACKAGE)
          .should(notDeclareTransactionalAnywhere())
          .because(
              "background jobs open transactions through TenantScopedTransaction (the tenant-aware"
                  + " primitive), never through @Transactional");

  /** Background jobs must not hand-roll transactions either: the primitive is the only door. */
  static final ArchRule NO_RAW_TRANSACTION_PLUMBING_IN_JOBS =
      noClasses()
          .that()
          .resideInAPackage(JOBS_PACKAGE)
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(TransactionTemplate.class, PlatformTransactionManager.class)
          .because(
              "background jobs open transactions through TenantScopedTransaction (the tenant-aware"
                  + " primitive), not through a raw TransactionTemplate");

  /**
   * HTTP-side classes: the rest/api packages, plus any controller-annotated class living elsewhere
   * (real ones exist: SchemaApi, FullTextSearchApi, OpenCTIApi). Package-only matching provably
   * missed them.
   */
  static final DescribedPredicate<JavaClass> HTTP_SIDE_CLASSES =
      JavaClass.Predicates.resideInAnyPackage("io.openaev.rest..", "io.openaev.api..")
          .or(
              CanBeAnnotated.Predicates.annotatedWith(
                  "org.springframework.web.bind.annotation.RestController"))
          .or(CanBeAnnotated.Predicates.annotatedWith("org.springframework.stereotype.Controller"))
          .or(
              CanBeAnnotated.Predicates.metaAnnotatedWith(
                  "org.springframework.stereotype.Controller"))
          .as("HTTP-side classes (io.openaev.rest.., io.openaev.api.., or controller-annotated)");

  /**
   * The primitive is background-only. From a controller, {@code execute(allTenants(), ...)} would
   * run outside any transaction, pass the active-transaction guard, and silently widen the read to
   * every tenant: exactly the silent widening the design forbids. HTTP carries its scope through
   * {@code @Transactional} + {@code TxCtx} and the aspect. Known limit: the rule sees DIRECT
   * dependencies only; a service reachable from a non-transactional controller could still carry
   * the primitive (declared in the PR, structural answer on the arbitration list).
   */
  static final ArchRule NO_PRIMITIVE_ON_HTTP_PATH =
      noClasses()
          .that(HTTP_SIDE_CLASSES)
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(TenantScopedTransaction.class)
          .because(
              "the background primitive must not be reachable from the HTTP path; HTTP carries its"
                  + " scope through @Transactional + TxCtx (the aspect)");

  /**
   * The v1 way of declaring a read cross-tenant, {@code session.disableFilter("tenantFilter")}, is
   * frozen: a call site outside the reasoned baseline fails the build, and a baselined site covers
   * exactly one call. The call is no way to widen a read: off a transaction it lands on a throwaway
   * session, inside the background primitive the session has no filter to disable, and the next
   * method-level {@code @Transactional} re-arms it ({@code TenantFilterToggleReachTest}). A
   * cross-tenant read goes through the primitive with an explicit scope.
   *
   * <p>Detection limits (bytecode level): the call is matched by name on any Hibernate session
   * receiver ({@code SharedSessionContract}, which covers {@code Session} and {@code
   * StatelessSession}), not by its {@code "tenantFilter"} argument, which ArchUnit cannot read;
   * {@code tenantFilter} is the only filter this codebase defines, so the match is exact today and
   * fails closed if another is ever added. A call made through reflection is not seen.
   */
  static ArchRule noTenantFilterDisableOutsideBaseline(Set<String> baseline) {
    return classes()
        .should(notDisableTheTenantFilterOutside(baseline))
        .because(
            "disableFilter(\"tenantFilter\") does not widen a read; a cross-tenant read goes through"
                + " TenantScopedTransaction with an explicit scope, and the reasoned baseline of the"
                + " remaining calls only shrinks");
  }

  /** True when the call disables a Hibernate session filter. */
  static boolean isTenantFilterDisable(JavaMethodCall call) {
    return "disableFilter".equals(call.getName())
        && call.getTargetOwner().isAssignableTo(SharedSessionContract.class);
  }

  /**
   * The {@code fully.qualified.Class#method} that holds the call, the grain of a baseline line. A
   * call written inside a lambda belongs to the method that declares the lambda, not to the
   * synthetic {@code lambda$method$n} the compiler emits for it.
   */
  static String siteOf(JavaMethodCall call) {
    String method = call.getOrigin().getName();
    if (method.startsWith("lambda$")) {
      method = method.substring("lambda$".length(), method.indexOf('$', "lambda$".length()));
    }
    return call.getOriginOwner().getFullName() + "#" + method;
  }

  /** Every site among {@code classes} that disables a session filter, with its number of calls. */
  static Map<String, Integer> tenantFilterDisableSites(JavaClasses classes) {
    Map<String, Integer> sites = new TreeMap<>();
    for (JavaClass clazz : classes) {
      for (JavaMethodCall call : clazz.getMethodCallsFromSelf()) {
        if (isTenantFilterDisable(call)) {
          sites.merge(siteOf(call), 1, Integer::sum);
        }
      }
    }
    return sites;
  }

  private static ArchCondition<JavaClass> notDisableTheTenantFilterOutside(Set<String> baseline) {
    return new ArchCondition<>("not disable the tenant filter outside the reasoned baseline") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        Map<String, Integer> callsPerSite = new TreeMap<>();
        for (JavaMethodCall call : clazz.getMethodCallsFromSelf()) {
          if (!isTenantFilterDisable(call)) {
            continue;
          }
          String site = siteOf(call);
          int calls = callsPerSite.merge(site, 1, Integer::sum);
          if (!baseline.contains(site)) {
            events.add(
                SimpleConditionEvent.violated(
                    call, site + " disables the tenant filter: " + call.getDescription()));
          } else if (calls > 1) {
            events.add(
                SimpleConditionEvent.violated(
                    call,
                    site
                        + " disables the tenant filter more than once, a baseline line covers a"
                        + " single call: "
                        + call.getDescription()));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notSelfInvokeATransactionalMethod() {
    return new ArchCondition<>("not call a @Transactional method of the same class") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        for (JavaMethodCall call : clazz.getMethodCallsFromSelf()) {
          if (!call.getOriginOwner().equals(call.getTargetOwner())) {
            continue;
          }
          call.getTarget()
              .resolveMember()
              .filter(TenantBackgroundTransactionRules::isTransactional)
              .ifPresent(
                  target ->
                      events.add(
                          SimpleConditionEvent.violated(
                              call,
                              call.getDescription()
                                  + " self-invokes @Transactional "
                                  + target.getFullName())));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notDeclareTransactionalAnywhere() {
    return new ArchCondition<>("not declare @Transactional on the class or any method") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        if (isTransactional(clazz)) {
          events.add(
              SimpleConditionEvent.violated(
                  clazz, clazz.getFullName() + " is annotated with @Transactional"));
        }
        for (JavaMethod method : clazz.getMethods()) {
          if (isTransactional(method)) {
            events.add(
                SimpleConditionEvent.violated(
                    method, method.getFullName() + " is annotated with @Transactional"));
          }
        }
      }
    };
  }

  private static boolean isTransactional(JavaClass clazz) {
    return clazz.isAnnotatedWith("org.springframework.transaction.annotation.Transactional")
        || clazz.isAnnotatedWith("jakarta.transaction.Transactional");
  }

  private static boolean isTransactional(JavaMethod method) {
    return method.isAnnotatedWith("org.springframework.transaction.annotation.Transactional")
        || method.isAnnotatedWith("jakarta.transaction.Transactional");
  }
}
