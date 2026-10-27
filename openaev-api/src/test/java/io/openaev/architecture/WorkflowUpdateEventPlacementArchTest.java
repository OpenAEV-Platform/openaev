package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.aop.WorkflowUpdateEvent;
import io.openaev.service.chaining.WorkflowUpdateEventPublisher;
import java.util.Set;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards where {@link WorkflowUpdateEvent} may go. The aspect looks the chaining steps up, and
 * reads each event's tenant, in the annotated method's transaction. On a method that runs with no
 * transaction it would read with no tenant scope, find no step once the chaining tables are
 * tenant-active, and publish nothing, without any error. Such a method publishes through {@link
 * WorkflowUpdateEventPublisher} from a transaction scoped to its injects' tenant instead.
 *
 * <p>The rule sees a propagation declared on the method that runs without a transaction, or may. It
 * cannot see a method that declares nothing and inherits its caller's transaction, which is how
 * most annotated methods work, nor whether that transaction carries a tenant scope.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class WorkflowUpdateEventPlacementArchTest {

  private static final Set<Propagation> WITHOUT_TRANSACTION =
      Set.of(Propagation.NOT_SUPPORTED, Propagation.NEVER, Propagation.SUPPORTS);

  @ArchTest
  static final ArchRule workflow_update_event_runs_inside_a_transaction =
      methods()
          .that()
          .areAnnotatedWith(WorkflowUpdateEvent.class)
          .should(
              new ArchCondition<JavaMethod>("not run outside a transaction") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                  method
                      .tryGetAnnotationOfType(Transactional.class)
                      .filter(tx -> WITHOUT_TRANSACTION.contains(tx.propagation()))
                      .ifPresent(
                          tx ->
                              events.add(
                                  SimpleConditionEvent.violated(
                                      method,
                                      method.getFullName()
                                          + " carries @WorkflowUpdateEvent but runs with"
                                          + " propagation "
                                          + tx.propagation()
                                          + ": the aspect would look the steps up with no"
                                          + " tenant scope. Publish through"
                                          + " WorkflowUpdateEventPublisher from a scoped"
                                          + " transaction instead")));
                }
              });
}
