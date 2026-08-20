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
          // attackpath_execution / attackpath_finding (v2): every read of the projection, including
          // the delta cursor added with the real-time updates (#6647, spec 002). Losing the TxCtx
          // on
          // one of these would not fail loudly — the reads would simply return zero rows.
          "io.openaev.api.attackpath.AttackPathApi#graph",
          "io.openaev.api.attackpath.AttackPathApi#graphDelta",
          "io.openaev.api.attackpath.AttackPathApi#simulations",
          "io.openaev.api.attackpath.AttackPathApi#expandEndpointFindings",
          "io.openaev.api.attackpath.AttackPathApi#relations",
          "io.openaev.api.attackpath.AttackPathApi#findings",
          "io.openaev.api.attackpath.AttackPathApi#executionDetail",
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
          // injectors: all read/write endpoints wired with TxCtx
          "io.openaev.rest.injector.InjectorApi#injectors",
          "io.openaev.rest.injector.InjectorApi#injectorInjectTypes",
          "io.openaev.rest.injector.InjectorApi#updateInjector",
          "io.openaev.rest.injector.InjectorApi#injector",
          "io.openaev.rest.injector.InjectorApi#getInjectorRelatedIds",
          "io.openaev.rest.injector.InjectorApi#deleteInjector",
          "io.openaev.rest.injector.InjectorApi#registerInjector",
          "io.openaev.rest.injector.InjectorApi#optionsByName",
          "io.openaev.rest.injector.InjectorApi#optionsById",
          // injector reads through connector-instance and injector-contract paths
          "io.openaev.rest.connector_instance.ConnectorInstanceApi#createConnectorInstance",
          // injector_contracts: eager injectorLinks -> injector fetch on every load, found by the
          // Phase 3b association scan (#7026-class gap: reads were missed when only the create
          // endpoint had been wired)
          "io.openaev.rest.injector_contract.InjectorContractApi#injectContracts",
          "io.openaev.rest.injector_contract.InjectorContractApi#injectorContracts",
          "io.openaev.rest.injector_contract.InjectorContractApi#injectorContract",
          "io.openaev.rest.injector_contract.InjectorContractApi#createInjectorContract",
          "io.openaev.rest.injector_contract.InjectorContractApi#updateInjectorContract",
          "io.openaev.rest.injector_contract.InjectorContractApi#updateInjectorContractMapping",
          "io.openaev.rest.injector_contract.InjectorContractApi#deleteInjectorContract",
          // Phase 3b association scan on `injectors`: real HTTP entrypoints reading
          // Inject#getInjector() / InjectorContract#getFirstInjector() / #getInjectors() without
          // any TxCtx at all (#7026-class gap, confirmed by tracing each caller back to its
          // controller method)
          "io.openaev.rest.atomic_testing.AtomicTestingApi#atomicTestingImport",
          // atomic-testing: create/update resolve the Injector via InjectUtils#resolveInjector;
          // duplicate/relaunch read Inject#getInjector(), a lazy association, both on the
          // v2-scoped injectors table (found manually testing the create-atomic-testing flow,
          // #7026-class gap - the original activation only wired atomicTestingImport and
          // collectorsFromAtomicTesting for this controller)
          "io.openaev.rest.atomic_testing.AtomicTestingApi#createAtomicTesting",
          "io.openaev.rest.atomic_testing.AtomicTestingApi#updateAtomicTesting",
          "io.openaev.rest.atomic_testing.AtomicTestingApi#duplicateAtomicTesting",
          "io.openaev.rest.inject_test_status.ScenarioInjectTestApi#testInject",
          "io.openaev.rest.inject_test_status.ScenarioInjectTestApi#bulkTestInject",
          "io.openaev.rest.inject_test_status.SimulationInjectTestApi#testInject",
          "io.openaev.rest.inject_test_status.SimulationInjectTestApi#bulkTestInject",
          "io.openaev.rest.scenario.ScenarioImportApi#injectsImport",
          "io.openaev.rest.exercise.ExerciseImportApi#injectsImport",
          "io.openaev.rest.mapper.MapperApi#testImportXLSFile",
          "io.openaev.api.threat_arsenal.ThreatArsenalApiImporter#importJson",
          "io.openaev.rest.asset.endpoint.EndpointApi#upsertEndpoint",
          "io.openaev.rest.asset.endpoint.EndpointApi#createEndpoint",
          "io.openaev.rest.asset.endpoint.EndpointApi#upsertAgentLessEndpoint",
          "io.openaev.rest.asset.endpoint.EndpointApi#getEndpointJobs",
          "io.openaev.rest.asset.endpoint.EndpointApi#cleanupAssetAgentJob",
          "io.openaev.rest.asset.endpoint.EndpointApi#cleanupAssetAgentJobDepreacted",
          "io.openaev.rest.asset.endpoint.EndpointApi#endpoints",
          "io.openaev.rest.asset.endpoint.EndpointApi#endpoint",
          "io.openaev.rest.asset.endpoint.EndpointApi#assets",
          "io.openaev.rest.asset.endpoint.EndpointApi#targetEndpoints",
          "io.openaev.rest.asset.endpoint.EndpointApi#findEndpoints",
          "io.openaev.rest.asset.endpoint.EndpointApi#updateEndpoint",
          "io.openaev.rest.asset.endpoint.EndpointApi#deleteEndpoint",
          "io.openaev.rest.asset.endpoint.EndpointApi#asset",
          "io.openaev.rest.asset.endpoint.EndpointApi#searchInjectsForAsset",
          "io.openaev.rest.asset.endpoint.EndpointApi#deleteAsset",
          "io.openaev.rest.connector_instance.ConnectorInstanceApi#deleteConnectorInstance",
          // connector_instances (v2 activation): all 7 endpoints wired with TxCtx (reads,
          // create/delete, requested-status and configurations writes, log search)
          "io.openaev.rest.connector_instance.ConnectorInstanceApi#getConnectorInstance",
          "io.openaev.rest.connector_instance.ConnectorInstanceApi#getConnectorInstanceConfiguration",
          "io.openaev.rest.connector_instance.ConnectorInstanceApi#updateConnectorInstanceConfigurations",
          "io.openaev.rest.connector_instance.ConnectorInstanceApi#searchConnectorInstanceLogs",
          "io.openaev.rest.connector_instance.ConnectorInstanceApi#updateRequestedStatus",
          // connector_instances (v2 activation, Phase 3b two-hop finding): CatalogConnectorApi
          // reads connector_instances indirectly (CatalogConnectorService ->
          // ConnectorInstanceService#connectorInstances/#findAllByCatalogConnectorId) to compute
          // instance_deployed_count per catalog connector; the repository-name grep alone missed
          // this two-hop caller since it goes through CatalogConnectorService, not
          // ConnectorInstanceRepository directly
          "io.openaev.rest.catalog_connector.CatalogConnectorApi#getCatalogConnectors",
          "io.openaev.rest.catalog_connector.CatalogConnectorApi#getConnector",
          // executors (v2, gap sweep #7059 parity pass): scenario/exercise raw-endpoint reads and
          // EE-executor-gate paths (throwIfScenarioNotLaunchable / throwIfExerciseNotLaunchable /
          // throwIfInjectNotLaunchable -> detectEEExecutors -> agent.getExecutor()), plus the agent
          // target search path.
          "io.openaev.rest.scenario.ScenarioApi#endpoints",
          "io.openaev.rest.scenario.ScenarioApi#endpointsByIds",
          // workflow scope inventory (chaining RBAC, #4824): same EndpointMapper agent/executor
          // walk as the scenario/exercise endpoint reads above.
          "io.openaev.api.chaining.WorkflowApi#getScopeEndpoints",
          "io.openaev.api.chaining.WorkflowApi#findScopeEndpoints",
          "io.openaev.rest.scenario.ScenarioApi#updateScenarioRecurrence",
          "io.openaev.rest.scenario.ScenarioApi#createRunningExerciseFromScenario",
          "io.openaev.rest.exercise.ExerciseApi#endpoints",
          "io.openaev.rest.exercise.ExerciseApi#endpointsByIds",
          "io.openaev.rest.exercise.ExerciseApi#updateExerciseStart",
          "io.openaev.rest.exercise.ExerciseApi#deprecatedUpdateExerciseStart",
          // changeExerciseStatus (manual SCHEDULED->RUNNING launch) hits the same
          // throwIfExerciseNotLaunchable -> throwIfInjectNotLaunchable -> detectEEExecutors ->
          // agent.getExecutor() gate; it was missed in the #7059 parity pass (regression fix).
          "io.openaev.rest.exercise.ExerciseApi#changeExerciseStatus",
          "io.openaev.rest.atomic_testing.AtomicTestingApi#launchAtomicTesting",
          "io.openaev.rest.atomic_testing.AtomicTestingApi#relaunchAtomicTesting",
          "io.openaev.rest.atomic_testing.AtomicTestingApi#updateAtomicTestingRecurrence",
          "io.openaev.rest.inject.InjectApi#injectTargetSearch",
          // payload: upsert reads collectors via PayloadUpsertService; collectorsFromPayload reads
          // directly
          "io.openaev.rest.payload.PayloadApi#upsertPayload",
          "io.openaev.rest.payload.PayloadApi#collectorsFromPayload",
          // payload: create/update/duplicate all resynchronize the injector contract against the
          // tenant's payload-supporting injectors via
          // PayloadService#synchroniseInjectorContractBasedOnPayload (found alongside the
          // ThreatArsenalApi gap below - same shared service, same missing scope)
          "io.openaev.rest.payload.PayloadApi#createPayload",
          "io.openaev.rest.payload.PayloadApi#updatePayload",
          "io.openaev.rest.payload.PayloadApi#duplicatePayload",
          // threat arsenal: create/update/duplicate go through PayloadCreationService/
          // PayloadUpdateService into the same synchroniseInjectorContractBasedOnPayload path
          "io.openaev.api.threat_arsenal.ThreatArsenalApi#createAction",
          "io.openaev.api.threat_arsenal.ThreatArsenalApi#updateAction",
          "io.openaev.api.threat_arsenal.ThreatArsenalApi#duplicateAction",
          // threat arsenal reads: same InjectorContractService projection/association as
          // InjectorContractApi#injectorContracts (already listed above), reached through a
          // separate sibling controller - resolves injector_contract_injector_type via the v2
          // tenant-scoped injectors table. Missed on the original injectors activation (#6410)
          // because the inventory stopped at the expected InjectorContractApi caller and never
          // re-ran the caller-search on the shared InjectorContractService search/association
          // methods themselves (regression fixed here).
          "io.openaev.api.threat_arsenal.ThreatArsenalApi#threatArsenal",
          "io.openaev.api.threat_arsenal.ThreatArsenalApi#threatArsenals",
          "io.openaev.api.threat_arsenal.ThreatArsenalApi#threatArsenalsNonTabletop",
          // threat arsenal delete/bulk-delete: isEligibleForDeletion resolves
          // InjectorContract#getInjectorType() (delete) and InjectorContractService#getSinglePage
          // (bulkDelete), both v2 tenant-scoped through the injectors table. Missed by the same
          // #6410 re-inventory gap as the reads above (Phase 1 re-run, see the skill's hardened
          // caller-search procedure).
          "io.openaev.api.threat_arsenal.ThreatArsenalApi#deleteAction",
          "io.openaev.api.threat_arsenal.ThreatArsenalApi#bulkDeleteActions",
          // scenario "add threat arsenal to scenario(s)": InjectService#buildInject resolves the
          // injector via InjectUtils#resolveInjector, v2 tenant-scoped through injectors. Same
          // #6410 re-inventory gap: InjectUtils#resolveInjector was never re-walked as a shared
          // symbol once one caller (SimulationInjectApi#createInjectForExercise) was already wired.
          "io.openaev.rest.scenario.ScenarioApi#createScenarioWithInjectorContracts",
          "io.openaev.rest.scenario.ScenarioApi#updateScenariosWithInjectorContracts",
          // scenario/exercise ZIP-JSON import: V1_DataImporter resolves
          // InjectorContract#getFirstInjector() and InjectorService#injectorTypeExists(...), both
          // v2 tenant-scoped through injectors. Sibling XLS injectsImport endpoints
          // (ScenarioImportApi/ExerciseImportApi, already listed above) were wired; these ZIP/JSON
          // import endpoints were not (#6410 re-inventory gap).
          "io.openaev.rest.scenario.ScenarioApi#importScenario",
          "io.openaev.rest.exercise.ExerciseApi#exerciseImport",
          // bulk inject creation from a threat-arsenal search: createAndSaveInjectList resolves the
          // injector via InjectUtils#resolveInjector; sibling single-inject creation endpoints
          // (createInjectForExercise/createInjectForScenario, already listed above) were wired,
          // these bulk endpoints were not (#6410 re-inventory gap).
          "io.openaev.rest.inject.SimulationInjectApi#createInjectsForExercise",
          "io.openaev.rest.inject.ScenarioInjectApi#createInjectsForScenario",
          "io.openaev.rest.inject.ScenarioInjectApi#generateInjectsForScenario",
          // direct inject execution (mass-run "launch" action): resolveInjector then
          // executor.directExecute both resolve the injector through the v2 tenant-scoped
          // injectors table (#6410 re-inventory gap).
          "io.openaev.rest.inject.SimulationInjectApi#executeInject",
          // autonomous-run capability resolution: buildArsenalInventory reads
          // injectorRepository.findAll() directly, v2 tenant-scoped through injectors. Every other
          // AutonomousRunApi endpoint already carries TxCtx; this one was the odd one out (#6410
          // re-inventory gap).
          "io.openaev.api.autonomous.AutonomousRunApi#resolveCapabilities",
          // exercise lessons-learned "send" action: MailingService#sendEmail resolves the email
          // injector contract's linked injector, v2 tenant-scoped through injectors (#6410
          // re-inventory gap).
          "io.openaev.rest.lessons.ExerciseLessonsApi#sendExerciseLessons",
          // phishing landing pages: create/update/logos/duplicate all resolve to
          // PhishingLandingPageService#upsert -> synchroniseInjectorContract, which reads the
          // tenant's phishing injector via injectorRepository, v2 tenant-scoped through injectors.
          // No endpoint in this controller carried TxCtx before this fix (#6410 re-inventory gap).
          "io.openaev.injectors.phishing.api.PhishingLandingPageApi#createLandingPage",
          "io.openaev.injectors.phishing.api.PhishingLandingPageApi#updateLandingPage",
          "io.openaev.injectors.phishing.api.PhishingLandingPageApi#updateLandingPageLogos",
          "io.openaev.injectors.phishing.api.PhishingLandingPageApi#duplicateLandingPage",
          // phishing email templates: create/update/duplicate/delete/bulk-delete all resync every
          // landing page's contract (PhishingEmailTemplateService#resyncLandingPageContracts ->
          // PhishingLandingPageService#resyncAllContracts -> synchroniseInjectorContract), same
          // injectors-table read as the landing page endpoints above (#6410 re-inventory gap).
          "io.openaev.injectors.phishing.api.PhishingEmailTemplateApi#createEmailTemplate",
          "io.openaev.injectors.phishing.api.PhishingEmailTemplateApi#updateEmailTemplate",
          "io.openaev.injectors.phishing.api.PhishingEmailTemplateApi#duplicateEmailTemplate",
          "io.openaev.injectors.phishing.api.PhishingEmailTemplateApi#deleteEmailTemplate",
          "io.openaev.injectors.phishing.api.PhishingEmailTemplateApi#bulkDeleteEmailTemplates",
          // stix: security-coverage processing creates DNS-resolution/drop-file payloads via
          // PayloadService#getDynamicDnsResolutionPayload / getFileDropPayloadByDocument, which
          // lazily create the payload's injector contract through the same
          // synchroniseInjectorContractBasedOnPayload path as the two entries above
          "io.openaev.api.stix_process.StixApi#processBundle",
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
          "io.openaev.rest.asset.security_platforms.SecurityPlatformApi#updateSecurityPlatform",
          // autonomous_runs / autonomous_events / autonomous_directives (v2, #7396): every handler
          // that reads or writes those tables carries TxCtx so the inspector can see rows. The
          // scenario delete paths tear the run down and must keep the same scope.
          "io.openaev.api.autonomous.AutonomousRunApi#create",
          "io.openaev.api.autonomous.AutonomousRunApi#launchFromScenario",
          "io.openaev.api.autonomous.AutonomousRunApi#planScenario",
          "io.openaev.api.autonomous.AutonomousRunApi#list",
          "io.openaev.api.autonomous.AutonomousRunApi#get",
          "io.openaev.api.autonomous.AutonomousRunApi#getBySimulation",
          "io.openaev.api.autonomous.AutonomousRunApi#getByScenario",
          "io.openaev.api.autonomous.AutonomousRunApi#start",
          "io.openaev.api.autonomous.AutonomousRunApi#pause",
          "io.openaev.api.autonomous.AutonomousRunApi#resume",
          "io.openaev.api.autonomous.AutonomousRunApi#cancel",
          "io.openaev.api.autonomous.AutonomousRunApi#restart",
          "io.openaev.api.autonomous.AutonomousRunApi#promote",
          "io.openaev.api.autonomous.AutonomousRunApi#convertToManual",
          "io.openaev.api.autonomous.AutonomousRunApi#timeline",
          "io.openaev.api.autonomous.AutonomousRunApi#directives",
          "io.openaev.api.autonomous.AutonomousRunApi#addDirective",
          "io.openaev.api.autonomous.AutonomousRunApi#updateConfiguration",
          "io.openaev.api.autonomous.AutonomousRunApi#getScope",
          "io.openaev.api.autonomous.AutonomousRunApi#setScope",
          "io.openaev.api.autonomous.AutonomousRunApi#recordEvent",
          "io.openaev.api.autonomous.AutonomousRunApi#updateStatus",
          "io.openaev.api.autonomous.AutonomousRunApi#consumeDirectives",
          "io.openaev.api.autonomous.AutonomousRunApi#appendAttackPathStep",
          "io.openaev.api.autonomous.AutonomousRunApi#updateAttackPathStep",
          "io.openaev.api.autonomous.AutonomousRunApi#deleteAttackPathStep",
          "io.openaev.api.autonomous.AutonomousRunApi#attackPathState",
          "io.openaev.api.autonomous.AutonomousRunApi#evaluateAttackPath",
          "io.openaev.api.autonomous.AutonomousRunApi#promoteFindingToAsset",
          "io.openaev.api.autonomous.AutonomousRunApi#ensureTargetTeam",
          "io.openaev.rest.scenario.ScenarioApi#deleteScenario",
          "io.openaev.rest.scenario.ScenarioApi#bulkDeleteScenarios");

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
