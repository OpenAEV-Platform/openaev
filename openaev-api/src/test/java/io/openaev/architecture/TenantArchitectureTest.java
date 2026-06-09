package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.context.TxCtx;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Architectural rules enforcing transactional tenant context discipline.
 *
 * <p>These rules guarantee two invariants at build time:
 *
 * <ol>
 *   <li>Every REST endpoint explicitly declares a {@link Transactional} boundary.
 *   <li>Every {@link Transactional} method receives a {@link TxCtx} so that the AOP aspect can
 *       inject the tenant context into the database session.
 * </ol>
 */
@AnalyzeClasses(packages = "io.openaev")
public class TenantArchitectureTest {

  private static final ArchCondition<JavaMethod> HAVE_TRANSACTIONAL_ANNOTATION =
      new ArchCondition<>("be annotated with @Transactional") {
        @Override
        public void check(JavaMethod method, ConditionEvents events) {
          boolean hasSpringTransactional = method.isAnnotatedWith(Transactional.class);
          boolean hasJakartaTransactional =
              method.isAnnotatedWith(jakarta.transaction.Transactional.class);
          if (!hasSpringTransactional && !hasJakartaTransactional) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    String.format(
                        "REST endpoint '%s' in %s must be annotated with @Transactional",
                        method.getName(), method.getOwner().getSimpleName())));
          }
        }
      };

  private static final ArchCondition<JavaMethod> HAVE_TX_CTX_PARAMETER =
      new ArchCondition<>("declare a TxCtx parameter") {
        @Override
        public void check(JavaMethod method, ConditionEvents events) {
          boolean hasTxCtx =
              method.getParameterTypes().stream()
                  .anyMatch(p -> p.getName().equals(TxCtx.class.getName()));
          if (!hasTxCtx) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    String.format(
                        "@Transactional method '%s' in %s must declare a TxCtx parameter"
                            + " to ensure tenant context is available",
                        method.getName(), method.getOwner().getSimpleName())));
          }
        }
      };

  // -------------------------------------------------------------------------
  // Rule 1: All REST endpoints must be explicitly @Transactional
  // -------------------------------------------------------------------------

  @ArchTest
  static final ArchRule post_mappings_must_be_transactional =
      methods()
          .that()
          .areAnnotatedWith(PostMapping.class)
          .should(HAVE_TRANSACTIONAL_ANNOTATION)
          .because("every REST endpoint must explicitly declare a transactional boundary");

  @ArchTest
  static final ArchRule get_mappings_must_be_transactional =
      methods()
          .that()
          .areAnnotatedWith(GetMapping.class)
          .should(HAVE_TRANSACTIONAL_ANNOTATION)
          .because("every REST endpoint must explicitly declare a transactional boundary");

  @ArchTest
  static final ArchRule put_mappings_must_be_transactional =
      methods()
          .that()
          .areAnnotatedWith(PutMapping.class)
          .should(HAVE_TRANSACTIONAL_ANNOTATION)
          .because("every REST endpoint must explicitly declare a transactional boundary");

  @ArchTest
  static final ArchRule delete_mappings_must_be_transactional =
      methods()
          .that()
          .areAnnotatedWith(DeleteMapping.class)
          .should(HAVE_TRANSACTIONAL_ANNOTATION)
          .because("every REST endpoint must explicitly declare a transactional boundary");

  @ArchTest
  static final ArchRule patch_mappings_must_be_transactional =
      methods()
          .that()
          .areAnnotatedWith(PatchMapping.class)
          .should(HAVE_TRANSACTIONAL_ANNOTATION)
          .because("every REST endpoint must explicitly declare a transactional boundary");

  // -------------------------------------------------------------------------
  // Rule 2: All @Transactional methods must have a TxCtx parameter
  // -------------------------------------------------------------------------

  @ArchTest
  static final ArchRule transactional_methods_must_have_tx_ctx =
      methods()
          .that()
          .areAnnotatedWith(Transactional.class)
          .should(HAVE_TX_CTX_PARAMETER)
          .because(
              "the TxCtxTransactionAspect requires a TxCtx parameter"
                  + " to inject the tenant context into the database session via set_config()");

  @ArchTest
  static final ArchRule jakarta_transactional_methods_must_have_tx_ctx =
      methods()
          .that()
          .areAnnotatedWith(jakarta.transaction.Transactional.class)
          .should(HAVE_TX_CTX_PARAMETER)
          .because(
              "the TxCtxTransactionAspect requires a TxCtx parameter"
                  + " to inject the tenant context into the database session via set_config()");
}
