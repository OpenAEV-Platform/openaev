package io.openaev.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.openaev.rest.atomic_testing.AtomicTestingApi;
import io.openaev.rest.exercise.ExerciseApi;
import io.openaev.rest.inject.ScenarioInjectApi;
import io.openaev.rest.inject.SimulationInjectApi;
import io.openaev.rest.scenario.ScenarioApi;
import io.openaev.service.FaithfulCopyTransaction;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioLaunchService;

/**
 * A faithful copy reads with every marking of the request's tenants, beyond the user's clearance.
 * It is sanctioned for the copy endpoints only (duplicate, relaunch, scenario launch) and for the
 * autonomous run entry points that copy a scenario into a simulation (create, restart, promote),
 * which hand back nothing but the id of the copy, and for the realignment of a workflow template
 * after a scope save ({@code WorkflowService}), which resolves the saved scope with every marking
 * to store its asset ids on the step templates and returns none of them: any new caller must be a
 * deliberate, reviewed addition to this list.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class FaithfulCopyCallersArchTest {

  @ArchTest
  static final ArchRule only_copy_endpoints_open_a_faithful_copy =
      noClasses()
          .that(
              not(
                  belongToAnyOf(
                      FaithfulCopyTransaction.class,
                      AtomicTestingApi.class,
                      ScenarioApi.class,
                      ScenarioLaunchService.class,
                      ExerciseApi.class,
                      ScenarioInjectApi.class,
                      SimulationInjectApi.class,
                      AutonomousRunService.class,
                      WorkflowService.class)))
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(FaithfulCopyTransaction.class)
          .because(
              "a faithful copy reads beyond the user's clearance; only the copy endpoints, which"
                  + " return nothing but the id of the copy, may open one");
}
