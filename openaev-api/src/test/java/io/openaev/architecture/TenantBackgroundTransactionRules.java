package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.context.TenantScopedTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The two build-time guards behind the background transaction decision (#6398, widened #6391),
 * shared between the frozen-baseline arch test (production classes) and the fixture test that
 * proves each rule fires.
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
