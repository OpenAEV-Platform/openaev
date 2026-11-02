package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.context.TxCtx;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards the isolated transactions of the chaining services: every {@code @Transactional} method
 * opening a {@link Propagation#REQUIRES_NEW} transaction must declare a {@link TxCtx} parameter.
 *
 * <p>A new transaction does not inherit the caller's tenant scope, and the tenant aspect only
 * writes one from a {@link TxCtx} argument. Without it, every read of a tenant-active table inside
 * the new transaction returns nothing, with no error: an isolated chaining write then silently
 * skips the row it was meant to change. The scope an isolated chaining write runs in is the tenant
 * of the run or workflow it belongs to, so the caller passes it explicitly.
 *
 * <p>The rule fails on an empty selection, so it cannot pass vacuously if the isolated methods move
 * or are renamed.
 */
@AnalyzeClasses(
    packages = "io.openaev.service.chaining",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ChainingIsolatedTransactionTxCtxArchTest {

  @ArchTest
  static final ArchRule chaining_requires_new_methods_must_declare_tx_ctx =
      methods()
          .that(
              new DescribedPredicate<JavaMethod>("open a REQUIRES_NEW transaction") {
                @Override
                public boolean test(JavaMethod method) {
                  return method.isAnnotatedWith(Transactional.class)
                      && method.getAnnotationOfType(Transactional.class).propagation()
                          == Propagation.REQUIRES_NEW;
                }
              })
          .should(
              new ArchCondition<JavaMethod>("declare a TxCtx parameter") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                  boolean hasTxCtx =
                      method.getRawParameterTypes().stream()
                          .anyMatch(type -> type.isEquivalentTo(TxCtx.class));
                  if (!hasTxCtx) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            method.getFullName()
                                + " opens a REQUIRES_NEW transaction without a TxCtx parameter:"
                                + " the new transaction has no tenant scope, so its reads of"
                                + " tenant-active tables return nothing"));
                  }
                }
              });
}
