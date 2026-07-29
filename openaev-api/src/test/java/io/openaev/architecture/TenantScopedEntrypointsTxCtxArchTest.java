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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards the v2 tenant-scoped entrypoints against accidental signature drift (Corinne, #6255). The
 * {@code TxCtx} parameter is how the tenant scope reaches the transaction aspect; it looks unused
 * in the handler, so a refactor could drop it. This test fails the build if any listed entrypoint
 * loses its {@code TxCtx}.
 *
 * <p>This is a floor, not the robust fix: the entrypoint list is hardcoded, so it protects against
 * deletion on a known method but not against a new scoped endpoint added without {@code TxCtx}. The
 * self-maintaining version (a {@code @RequiresTxScope} marker plus a compile-time rule in
 * openaev-annotation-processor) is tracked as a follow-up.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class TenantScopedEntrypointsTxCtxArchTest {

  private static final Set<String> TX_SCOPED_ENTRYPOINTS =
      Set.of(
          // import_mappers (v2)
          "io.openaev.rest.mapper.MapperApi#getImportMapper",
          "io.openaev.rest.mapper.MapperApi#getImportMapperById",
          "io.openaev.rest.mapper.MapperApi#createImportMapper",
          "io.openaev.rest.mapper.MapperApi#exportMappers",
          "io.openaev.rest.mapper.MapperApi#importMappers",
          "io.openaev.rest.mapper.MapperApi#duplicateMapper",
          "io.openaev.rest.mapper.MapperApi#updateImportMapper",
          "io.openaev.rest.mapper.MapperApi#deleteImportMapper",
          "io.openaev.rest.lessons_template.LessonsTemplateApi#createLessonsTemplate",
          "io.openaev.rest.lessons_template.LessonsTemplateApi#lessonsTemplates",
          "io.openaev.rest.lessons_template.LessonsTemplateApi#updateLessonsTemplate",
          "io.openaev.rest.lessons_template.LessonsTemplateApi#deleteLessonsTemplate",
          "io.openaev.rest.lessons_template.LessonsTemplateApi#createLessonsTemplateCategory",
          "io.openaev.rest.lessons.ScenarioLessonsApi#applyScenarioLessonsTemplate",
          "io.openaev.rest.lessons.ExerciseLessonsApi#applyExerciseLessonsTemplate",
          "io.openaev.rest.scenario.ScenarioImportApi#dryRunImportXLSFile",
          "io.openaev.rest.scenario.ScenarioImportApi#validateImportXLSFile",
          "io.openaev.rest.exercise.ExerciseImportApi#dryRunImportXLSFile",
          "io.openaev.rest.exercise.ExerciseImportApi#validateImportXLSFile",
          // cwes: reached through a vulnerability's @ManyToMany, so every vulnerability/CVE
          // read-or-write entrypoint that maps the association carries the scope.
          "io.openaev.rest.vulnerability.VulnerabilityApi#searchVulnerabilities",
          "io.openaev.rest.vulnerability.VulnerabilityApi#getVulnerability",
          "io.openaev.rest.vulnerability.VulnerabilityApi#getVulnerabilityByExternalId",
          "io.openaev.rest.vulnerability.VulnerabilityApi#createVulnerability",
          "io.openaev.rest.vulnerability.VulnerabilityApi#bulkInsertVulnerabilitiesForCollector",
          "io.openaev.rest.vulnerability.VulnerabilityApi#updateVulnerability",
          "io.openaev.rest.cve.CveApi#searchCves",
          "io.openaev.rest.cve.CveApi#getCve",
          "io.openaev.rest.cve.CveApi#getCvebyExternalId",
          "io.openaev.rest.cve.CveApi#createCve",
          "io.openaev.rest.cve.CveApi#bulkInsertCVEsForCollector",
          "io.openaev.rest.cve.CveApi#updateCve",
          // mitigations (v2)
          "io.openaev.rest.mitigation.MitigationApi#mitigations",
          "io.openaev.rest.mitigation.MitigationApi#mitigation",
          "io.openaev.rest.mitigation.MitigationApi#injectorContracts",
          "io.openaev.rest.mitigation.MitigationApi#createMitigation",
          "io.openaev.rest.mitigation.MitigationApi#updateMitigation",
          "io.openaev.rest.mitigation.MitigationApi#upsertMitigation",
          "io.openaev.rest.mitigation.MitigationApi#deleteMitigation",
          // collectors: all read/write endpoints wired with TxCtx
          "io.openaev.rest.collector.CollectorApi#collectors",
          "io.openaev.rest.collector.CollectorApi#getCollector",
          "io.openaev.rest.collector.CollectorApi#getCollectorRelatedIds",
          "io.openaev.rest.collector.CollectorApi#getCollectorImageById",
          "io.openaev.rest.collector.CollectorApi#updateCollector",
          "io.openaev.rest.collector.CollectorApi#registerCollector",
          "io.openaev.rest.collector.CollectorApi#deleteCollector",
          // inject_expectation_traces: reads collectors via the service
          "io.openaev.rest.inject_expectation_trace.InjectExpectationTraceApi#createInjectExpectationTraceForCollector",
          "io.openaev.rest.inject_expectation_trace.InjectExpectationTraceApi#bulkInsertInjectExpectationTraceForCollector",
          "io.openaev.rest.inject_expectation_trace.InjectExpectationTraceApi#getInjectExpectationTracesFromCollector",
          "io.openaev.rest.inject_expectation_trace.InjectExpectationTraceApi#getAlertLinksNumber",
          // executors: all read/write endpoints wired with TxCtx
          "io.openaev.rest.executor.ExecutorApi#executors",
          "io.openaev.rest.executor.ExecutorApi#getExecutor",
          "io.openaev.rest.executor.ExecutorApi#getExecutorRelatedIds",
          "io.openaev.rest.executor.ExecutorApi#updateExecutor",
          "io.openaev.rest.executor.ExecutorApi#deleteExecutor",
          "io.openaev.rest.executor.ExecutorApi#registerExecutor",
          "io.openaev.rest.asset.endpoint.EndpointApi#upsertEndpoint",
          "io.openaev.rest.connector_instance.ConnectorInstanceApi#deleteConnectorInstance",
          // payload: upsert reads collectors via PayloadUpsertService; collectorsFromPayload reads
          // directly
          "io.openaev.rest.payload.PayloadApi#upsertPayload",
          "io.openaev.rest.payload.PayloadApi#collectorsFromPayload",
          // atomic-testing: collectorsFromAtomicTesting reads collectors via CollectorService
          "io.openaev.rest.atomic_testing.AtomicTestingApi#collectorsFromAtomicTesting",
          // inject: updateInject calls injectService.runChecks -> securityPlatformCollectors
          "io.openaev.rest.inject.InjectApi#updateInject",
          // simulation injects: runChecks path
          "io.openaev.rest.inject.SimulationInjectApi#exerciseInject",
          "io.openaev.rest.inject.SimulationInjectApi#createInjectForExercise",
          "io.openaev.rest.inject.SimulationInjectApi#duplicateInjectForExercise",
          // scenario injects: runChecks path
          "io.openaev.rest.inject.ScenarioInjectApi#createInjectForScenario",
          "io.openaev.rest.inject.ScenarioInjectApi#duplicateInjectForScenario",
          "io.openaev.rest.inject.ScenarioInjectApi#updateInjectForScenario",
          // health-check streams: runChecks -> securityPlatformCollectors
          "io.openaev.rest.scenario.ScenarioApi#streamHealthChecks",
          "io.openaev.rest.exercise.ExerciseApi#streamHealthChecks",
          // expectations: the AI feed reads collectors natively (expected-security-platforms
          // guard, #7014); the update endpoints attach collector-sourced results. Both overloads
          // of updateInjectExpectation are covered by the single name entry.
          "io.openaev.rest.expectation.ExpectationApi#getAiDefenseExpectationsNotFilledForSource",
          "io.openaev.rest.expectation.ExpectationApi#updateInjectExpectation",
          // security platforms: serialize the collectors association (tenant-active table) so the
          // UI can keep collector-managed platforms read-only (#7025). Both overloads of
          // securityPlatforms (GET list and POST search) are covered by the single name entry.
          "io.openaev.rest.asset.security_platforms.SecurityPlatformApi#securityPlatforms",
          "io.openaev.rest.asset.security_platforms.SecurityPlatformApi#securityPlatform",
          "io.openaev.rest.asset.security_platforms.SecurityPlatformApi#updateSecurityPlatform");

  @ArchTest
  static final ArchRule tx_scoped_entrypoints_must_declare_tx_ctx =
      methods()
          .that(
              new DescribedPredicate<JavaMethod>("are v2 tenant-scoped entrypoints") {
                @Override
                public boolean test(JavaMethod method) {
                  return TX_SCOPED_ENTRYPOINTS.contains(
                      method.getOwner().getName() + "#" + method.getName());
                }
              })
          .should(
              new ArchCondition<JavaMethod>("declare a TxCtx parameter") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                  boolean hasTxCtx =
                      method.getRawParameterTypes().stream()
                          .anyMatch(type -> TxCtx.class.getName().equals(type.getName()));
                  if (!hasTxCtx) {
                    String parameterList =
                        method.getRawParameterTypes().stream()
                            .map(javaClass -> javaClass.getName())
                            .collect(Collectors.joining(", "));
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "Entrypoint "
                                + method.getOwner().getName()
                                + "#"
                                + method.getName()
                                + "("
                                + parameterList
                                + ") must declare TxCtx to activate the tenant v2 transaction scope"));
                  }
                }
              });
}
