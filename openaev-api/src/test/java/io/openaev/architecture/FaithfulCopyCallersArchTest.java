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

/**
 * A faithful copy reads with every marking of the request's tenants, beyond the user's clearance.
 * It is sanctioned for the copy endpoints only (duplicate, relaunch, scenario launch), which hand
 * back nothing but the id of the copy: any new caller must be a deliberate, reviewed addition to
 * this list.
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
                      ExerciseApi.class,
                      ScenarioInjectApi.class,
                      SimulationInjectApi.class)))
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(FaithfulCopyTransaction.class)
          .because(
              "a faithful copy reads beyond the user's clearance; only the copy endpoints, which"
                  + " return nothing but the id of the copy, may open one");
}
