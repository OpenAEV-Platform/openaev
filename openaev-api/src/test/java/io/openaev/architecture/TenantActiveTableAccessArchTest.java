package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Vulnerability;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.database.repository.CweRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.LessonsTemplateRepository;
import io.openaev.database.repository.MitigationRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.executors.Executor;
import io.openaev.database.repository.autonomous.AutonomousDirectiveRepository;
import io.openaev.database.repository.autonomous.AutonomousEventRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.caldera.service.CalderaExecutorContextService;
import io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorContextService;
import io.openaev.executors.mde.service.MdeExecutorContextService;
import io.openaev.executors.openaev.service.OpenAEVExecutorContextService;
import io.openaev.executors.paloaltocortex.service.PaloAltoCortexExecutorContextService;
import io.openaev.executors.sentinelone.service.SentinelOneExecutorContextService;
import io.openaev.executors.tanium.service.TaniumExecutorContextService;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.injectors.phishing.service.PhishingLandingPageService;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.processor.core.V20260420_Migrate_rabbitmq_queues;
import io.openaev.processor.datapack.V20260330_Default_tenant_data;
import io.openaev.processor.datapack.V20260708_Dynamic_injectors_base_url;
import io.openaev.rest.asset.security_platforms.SecurityPlatformApi;
import io.openaev.rest.atomic_testing.AtomicTestingApi;
import io.openaev.rest.collector.CollectorApi;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.connector_instance.ConnectorInstanceApi;
import io.openaev.rest.executor.ExecutorApi;
import io.openaev.rest.exercise.ExerciseApi;
import io.openaev.rest.exercise.ExerciseImportApi;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.InjectApi;
import io.openaev.rest.inject.ScenarioInjectApi;
import io.openaev.rest.inject.SimulationInjectApi;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.ScenarioInjectService;
import io.openaev.rest.inject_expectation_trace.InjectExpectationTraceApi;
import io.openaev.rest.injector.InjectorApi;
import io.openaev.rest.injector_contract.InjectorContractApi;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.output.InjectorContractFullOutput;
import io.openaev.rest.lessons.ExerciseLessonsApi;
import io.openaev.rest.lessons.ScenarioLessonsApi;
import io.openaev.rest.lessons_template.LessonsTemplateApi;
import io.openaev.rest.mapper.MapperApi;
import io.openaev.rest.mitigation.MitigationApi;
import io.openaev.rest.payload.PayloadApi;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.payload.service.PayloadUpsertService;
import io.openaev.rest.scenario.ScenarioApi;
import io.openaev.rest.scenario.ScenarioImportApi;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.scheduler.jobs.ComchecksExecutionJob;
import io.openaev.service.EndpointService;
import io.openaev.service.InjectExpectationTraceService;
import io.openaev.service.InjectImportService;
import io.openaev.service.InjectTestStatusService;
import io.openaev.service.InjectorService;
import io.openaev.service.MailingService;
import io.openaev.service.MapperService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.attackpath.AttackPathCausalSeedService;
import io.openaev.service.attackpath.AttackPathDeltaService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService;
import io.openaev.service.attackpath.ingestion.AttackPathFindingIngestionService;
import io.openaev.service.autonomous.CapabilityResolverService;
import io.openaev.service.autonomous.AutonomousEventService;
import io.openaev.service.autonomous.AutonomousRunReconciliationWriter;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.autonomous.AutonomousTimeoutService;
import io.openaev.service.chaining.ScopeSnapshotService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.ConnectorOrchestrationService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.targets.search.AgentTargetSearchAdaptor;
import io.openaev.service.threat_arsenal.ThreatArsenalImportService;
import io.openaev.telemetry.metric_collectors.InventoryMetricCollector;
import io.openaev.telemetry.metric_collectors.ProductInventoryMetricCollector;
import io.openaev.utils.ExpectationUtils;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.mapper.VulnerabilityMapper;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Interim guard for the activation blind spot: once a table is in {@code
 * openaev.tenant.active-tables}, ANY access to it without a tenant scope silently reads zero rows
 * (fail-closed). Nothing at runtime ties "table is active" to "every accessor carries a scope", so
 * a NEW accessor added later would go dark with green tests. Until the structural fix lands (a
 * compile-time scope rule with #6391, and/or a fail-loud {@code can_access_tenant} — arbitration),
 * this test pins the REVIEWED access surface of each active table:
 *
 * <ul>
 *   <li>only allowlisted classes may depend on an active table's repository;
 *   <li>only allowlisted classes may call an association accessor that lazy-loads an active table
 *       (for {@code cwes}: {@code Vulnerability#getCwes}, which bypasses the repository entirely);
 *   <li>every table in the production allowlist MUST have a guard here: activating a table without
 *       extending this test fails the build (see the activate-tenant-table skill, go-live phase).
 * </ul>
 *
 * <p>Floor semantics, stated plainly: this test verifies WHO accesses an active table, not that the
 * access actually carries a scope at runtime. Each allowlist entry below documents its scope
 * mechanism; adding an entry is a review event, not a formality.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class TenantActiveTableAccessArchTest {

  /** Tables guarded by this test. Must cover every entry of the production allowlist. */
  private static final Set<String> GUARDED_TABLES =
      Set.of(
          "import_mappers",
          "lessons_templates",
          "cwes",
          "mitigations",
          "collectors",
          "executors",
          "injectors",
          "attackpath_execution",
          "attackpath_finding",
          "secret_references",
          "secrets",
          "connector_instances",
          "autonomous_runs",
          "autonomous_events",
          "autonomous_directives");

  @ArchTest
  static void every_active_table_is_guarded(JavaClasses classes) throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    Set<String> active =
        Arrays.stream(props.getProperty("openaev.tenant.active-tables", "").split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    assertTrue(
        GUARDED_TABLES.containsAll(active),
        "every table in openaev.tenant.active-tables needs a guard in this test; missing: "
            + active.stream().filter(t -> !GUARDED_TABLES.contains(t)).collect(Collectors.toSet())
            + ". Extend the repository/accessor rules and the allowlists (see the"
            + " activate-tenant-table skill, go-live phase).");
  }

  @ArchTest
  static final ArchRule import_mappers_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              MapperApi.class,
              ScenarioImportApi.class,
              ExerciseImportApi.class,
              // Service behind MapperApi; every caller is a wired handler:
              MapperService.class,
              // Documented degraded background reader (telemetry counts read 0 rows unscoped):
              ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(ImportMapperRepository.class)
          .because(
              "import_mappers is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule lessons_templates_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              LessonsTemplateApi.class, ExerciseLessonsApi.class, ScenarioLessonsApi.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(LessonsTemplateRepository.class)
          .because(
              "lessons_templates is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule cwes_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Resolves the write tenant explicitly (TxCtx threaded from the wired handlers):
              VulnerabilityService.class,
              // Provisioning datapack: writes cwes through cweRepository.save (still insert-only,
              // never reads), now under the primitive scope MigrationProcessor sets
              // (setScopeOnCurrentTransaction on onboarding, execute on startup), so it needs no
              // waiver. Allowlisted because it legitimately depends on CweRepository to seed cwes:
              V20260330_Default_tenant_data.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(CweRepository.class)
          .because(
              "cwes is tenant-active: an accessor without a tenant scope silently reads zero rows."
                  + " New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule cwes_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Render responses inside the scoped transactions of the wired handlers:
              VulnerabilityMapper.class)
          .should()
          .callMethod(Vulnerability.class, "getCwes")
          .because(
              "cwes is reached through Vulnerability's association WITHOUT touching the repository:"
                  + " a lazy getCwes() in an unscoped context silently loads zero rows. New callers"
                  + " must run inside a scoped transaction and be allowlisted here");

  @ArchTest
  static final ArchRule mitigations_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoint, pinned by TenantScopedEntrypointsTxCtxArchTest:
              MitigationApi.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(MitigationRepository.class)
          .because(
              "mitigations is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule collectors_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              CollectorApi.class,
              InjectExpectationTraceApi.class,
              PayloadApi.class,
              AtomicTestingApi.class,
              InjectApi.class,
              SimulationInjectApi.class,
              ScenarioInjectApi.class,
              ScenarioApi.class,
              ExerciseApi.class,
              // Intermediate services behind the TxCtx-carrying handlers above:
              CollectorService.class,
              InjectExpectationTraceService.class,
              InjectService.class,
              ScenarioInjectService.class,
              ScenarioService.class,
              ExerciseService.class,
              PayloadUpsertService.class,
              // Explicit tenantId param threaded from the caller (native DELETE ... AND
              // tenant_id = ?), not inspector-scoped: safe regardless of activation:
              ConnectorInstanceService.class,
              // Explicit tenantId param threaded from the launch path (native SELECT ... WHERE
              // tenant_id = ?), used to freeze the connected security platforms at RUN creation
              // (ADR-006): safe regardless of activation:
              ScopeSnapshotService.class,
              // Background telemetry reader scoped via tenantTx.execute(TxCtx.allTenants()):
              InventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(CollectorRepository.class)
          .because(
              "collectors is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule executors_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoint, pinned by TenantScopedEntrypointsTxCtxArchTest:
              ExecutorApi.class,
              // Service behind the handler; every caller is a wired handler:
              ExecutorService.class,
              // ConnectorInstanceService: invoked under TxCtx via ConnectorInstanceApi; deletes
              // executors via inspector-scoped deleteByExecutorId, so it relies on executors being
              // active on v2:
              ConnectorInstanceService.class,
              // EndpointService: reads executors via inspector-scoped findById (caller EndpointApi
              // carries TxCtx):
              EndpointService.class,
              // Background telemetry reader scoped via tenantTx.execute(TxCtx.allTenants()):
              InventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(ExecutorRepository.class)
          .because(
              "executors is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule injectors_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              InjectorApi.class,
              ConnectorInstanceApi.class,
              InjectorContractApi.class,
              // Service behind the handlers above:
              InjectorService.class,
              ConnectorOrchestrationService.class,
              InjectorContractService.class,
              // Inject execution path; scoped via TxCtx-bearing inject endpoints:
              InjectUtils.class,
              Executor.class,
              // Threat arsenal import endpoint carries TxCtx:
              ThreatArsenalImportService.class,
              // Payload path scoped by PayloadApi TxCtx entrypoints:
              PayloadUpsertService.class,
              PayloadService.class,
              // Connector teardown runs under ConnectorInstanceApi.deleteConnectorInstance scope:
              ConnectorInstanceService.class,
              // Background telemetry reader scoped via tenantTx.execute(TxCtx.allTenants()):
              InventoryMetricCollector.class,
              // Runtime migration scoped with tenantTx.executeNew(TxCtx.forTenant(...)):
              V20260420_Migrate_rabbitmq_queues.class,
              // Datapack writer scoped with tenantTx.executeNew(TxCtx.forTenant(...)):
              V20260708_Dynamic_injectors_base_url.class,
              // Builtin injector registration for tenant bootstrap is scoped in
              // createDependencyForTenant:
              ManagerFactory.class,
              // Security-platform registration links the registering injector using an explicit
              // tenant predicate (findByTypeAndTenantId with the platform row's own tenant), safe
              // under v2 regardless of the ambient scope:
              SecurityPlatformApi.class,
              // Autonomous arsenal inventory reads injectors via inspector-scoped findAll() under
              // the caller's TxCtx / per-tenant background scope; with no scope it fails closed
              // (empty inventory), never cross-tenant:
              CapabilityResolverService.class,
              // Phishing landing-page service synchronises its injector contract via the
              // tenant-explicit findByTypeAndTenantId(PhishingContract.TYPE, tenantId), mirroring
              // PayloadService's contract sync; the tenant is resolved from the landing page:
              PhishingLandingPageService.class,
              // Comcheck email generation resolves the built-in email injector via the
              // tenant-scoped findFirstByContractsCompositeIdIdAndTenantId under a per-comcheck
              // setScopeOnCurrentTransaction(forTenant(exercise tenant)) stamp; each comcheck gets
              // its own tenant's injector, never a fail-closed null:
              ComchecksExecutionJob.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(InjectorRepository.class)
          .because(
              "injectors is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  // Phase 3b (activate-tenant-table skill) finding: InjectorContract#getInjectors /
  // #getFirstInjector and Inject#getInjector reach the injectors table through an association,
  // not the repository, so the rule above cannot see these callers. Running this scan surfaced a
  // large pre-existing surface; each caller below was traced back to its real HTTP/job entrypoint.
  // REVIEWED-SAFE (moved out of the unverified block once traced):
  //  - Executor + all 7 *ExecutorContextService (incl. SentinelOneExecutorContextService, which
  //    also reads InjectorContract directly): only reached from
  //    InjectsExecutionJob#executeInject, which runs inside
  //    executeInTenant(tenantId, work) -> tenantTx.execute(TxCtx.forTenant(tenantId), work).
  //  - InjectorContractFullOutput#fromInjectorContract: dead code, zero callers anywhere in the
  //    codebase (main or test) — kept referenced here only for documentation, not a live path.
  //  - InjectImportService: every entrypoint (AtomicTestingApi#atomicTestingImport,
  //    ScenarioImportApi#injectsImport, ExerciseImportApi#injectsImport,
  //    MapperApi#testImportXLSFile) now carries TxCtx.
  //  - InjectTestStatusService: every entrypoint (ScenarioInjectTestApi#testInject/
  //    #bulkTestInject, SimulationInjectTestApi#testInject/#bulkTestInject) now carries TxCtx.
  //  - AgentTargetSearchAdaptor: its only caller, TargetService, is only reached from
  //    InjectApi#injectTargetSearch, which now carries TxCtx.
  // STILL PRE_EXISTING_UNVERIFIED_CALLERS (tracked follow-up: injector-phase3b-associations),
  // not a claim of safety. Do not add to this list without verifying the caller runs inside a
  // tenant-scoped transaction; fix it and move it to the reviewed-safe list above instead.
  //  - ScenarioToExerciseService: ScenarioApi#createRunningExerciseFromScenario is fixed, but
  //    ScenarioExecutionJob (the recurring-scenario cron job) still calls it under a single
  //    cross-tenant @Transactional with only the v1 TenantContext bridge set per scenario — no
  //    v2 TxCtx/TenantScopedTransaction at all. This is a real Phase 5b gap (background writer
  //    not converted to the primitive), tracked under injector-background-paths.
  @ArchTest
  static final ArchRule injectors_contract_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Owning-side mutation methods on the entity itself (addInjector/removeInjector/
              // clearInjectors/setInjectors), not an external caller:
              InjectorContract.class,
              // Reads injectorLinks inside the contract's own TxCtx-scoped handlers:
              InjectorContractService.class,
              // Reads inside InjectorApi/InjectorService's own TxCtx-scoped handlers:
              InjectorService.class,
              // --- REVIEWED-SAFE (see comment above) ---
              SentinelOneExecutorContextService.class,
              InjectorContractFullOutput.class,
              InjectImportService.class,
              InjectTestStatusService.class,
              AgentTargetSearchAdaptor.class,
              // --- PRE_EXISTING_UNVERIFIED_CALLERS (tracked follow-up, see comment above) ---
              // (ComchecksExecutionJob no longer belongs here: it resolves the email injector via
              // the tenant-scoped repository method instead of getFirstInjector; see the
              // injectors_repository rule allowlist.)
              MailingService.class,
              InjectUtils.class)
          .should()
          .callMethod(InjectorContract.class, "getInjectors")
          .orShould()
          .callMethod(InjectorContract.class, "getFirstInjector")
          .because(
              "injectors is tenant-active: InjectorContract#getInjectors/#getFirstInjector reach"
                  + " it through the injectorLinks association without touching the repository. A"
                  + " caller outside a tenant-scoped transaction silently sees an empty list. New"
                  + " callers must run inside a scoped transaction and be allowlisted here");

  @ArchTest
  static final ArchRule injectors_inject_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Inject entity's own accessor, not an external caller:
              Inject.class,
              // --- REVIEWED-SAFE (see comment above) ---
              Executor.class,
              CalderaExecutorContextService.class,
              CrowdStrikeExecutorContextService.class,
              MdeExecutorContextService.class,
              OpenAEVExecutorContextService.class,
              PaloAltoCortexExecutorContextService.class,
              SentinelOneExecutorContextService.class,
              TaniumExecutorContextService.class,
              InjectTestStatusService.class,
              // Scenario->exercise copy stamps the scenario's tenant
              // (setScopeOnCurrentTransaction(forTenant(...))) before reading getInjector(), so the
              // lazy load resolves under a real scope instead of fail-closing to null:
              ScenarioToExerciseService.class,
              // --- PRE_EXISTING_UNVERIFIED_CALLERS (tracked follow-up, see comment above) ---
              InjectExecutionStep.class,
              AttackPathExecution.class,
              HealthCheckUtils.class,
              AttackPathExecutionIngestionService.class,
              // Surfaced by merging main: a pre-existing getInjector() caller (attack-path causal
              // seed) that became reviewable once this PR activated injectors. Unverified scoping,
              // same tracked follow-up as its attack-path siblings above.
              AttackPathCausalSeedService.class,
              ExpectationUtils.class,
              InjectUtils.class)
          .should()
          .callMethod(Inject.class, "getInjector")
          .because(
              "injectors is tenant-active: Inject#injector is a lazy @ManyToOne resolved through"
                  + " the inspector once accessed beyond its id. A caller outside a tenant-scoped"
                  + " transaction silently sees a null injector. New callers must run inside a"
                  + " scoped transaction and be allowlisted here");

  @ArchTest
  static final ArchRule collectors_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Initializes the association inside its TxCtx-scoped transactions before the
              // open-in-view JSON rendering (pinned by TenantScopedEntrypointsTxCtxArchTest and
              // SecurityPlatformCollectorsTenantScopeTest, #7025):
              SecurityPlatformApi.class)
          .should()
          .callMethod(SecurityPlatform.class, "getCollectors")
          .because(
              "collectors is reached through SecurityPlatform's association WITHOUT touching the"
                  + " repository: a lazy getCollectors() in an unscoped context silently loads zero"
                  + " rows, which unlocks collector-managed platforms in the UI. New callers must"
                  + " run inside a scoped transaction and be allowlisted here");

  @ArchTest
  static final ArchRule injectors_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Initializes the association inside its TxCtx-scoped transactions before the
              // open-in-view JSON rendering, next to the collectors association (pinned by
              // SecurityPlatformInjectorLifecycleTest, #7063):
              SecurityPlatformApi.class)
          .should()
          .callMethod(SecurityPlatform.class, "getInjectors")
          .because(
              "security_platform_injectors feeds the same UI read-only signal as"
                  + " security_platform_collectors (#7063). injectors is not tenant-active yet, but"
                  + " this lazy association is rendered open-in-view: new callers must initialize it"
                  + " inside a scoped transaction and be allowlisted here so the #7025 blind spot"
                  + " cannot recur when the table is activated");

  @ArchTest
  static final ArchRule attackpath_execution_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Read path, driven by the TxCtx-carrying AttackPathApi (pinned by
              // TenantScopedEntrypointsTxCtxArchTest):
              AttackPathGraphService.class,
              // Same read path, same TxCtx-carrying controller: the delta endpoint's cursor reads
              // (pinned by AttackPathDeltaApiTest and AttackPathHttpIsolationTest):
              AttackPathDeltaService.class,
              // Background writer, scoped: opens its own transaction through the tenant primitive
              // with the inject's tenant, and stamps the row through TenantWriteScopeResolver.
              // Pinned by AttackPathIngestionTenantAttributionTest:
              AttackPathExecutionIngestionService.class,
              // Scoped reader: reads the step's execution rows inside its own executeNew (the
              // inject's tenant) with an explicit tenantId predicate, to attribute copied findings.
              // Pinned by AttackPathFindingIngestionServiceTest:
              AttackPathFindingIngestionService.class,
              // Seed generator, scoped: the admin flag-gated endpoint carries the TxCtx tenant
              // scope
              // (pinned by TenantScopedEntrypointsTxCtxArchTest) and the service only writes
              // executions with an explicit tenant on every row; its reads go through
              // AttackPathGraphService. Pinned by AttackPathCausalSeedApiTest:
              AttackPathCausalSeedService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(AttackPathExecutionRepository.class)
          .because(
              "attackpath_execution is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule attackpath_finding_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Read path, driven by the TxCtx-carrying AttackPathApi (pinned by
              // TenantScopedEntrypointsTxCtxArchTest):
              AttackPathGraphService.class,
              // Same read path, same TxCtx-carrying controller: the delta endpoint's cursor reads
              // (pinned by AttackPathDeltaApiTest and AttackPathHttpIsolationTest):
              AttackPathDeltaService.class,
              // Scoped writer: deletes a simulation's findings on reset/delete through the tenant
              // primitive (executeNew with the exercise's tenant). Pinned by
              // AttackPathIngestionTenantAttributionTest#deleteClearsTheSimulationScopedToItsTenant.
              AttackPathExecutionIngestionService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(AttackPathFindingRepository.class)
          .because(
              "attackpath_finding is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule connector_instances_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Own service; every entry point into it is either a TxCtx-carrying HTTP handler
              // (ConnectorInstanceApi, CatalogConnectorApi, pinned by
              // TenantScopedEntrypointsTxCtxArchTest) or an explicit
              // tenantTx.setScopeOnCurrentTransaction(TxCtx.allTenants()) for the platform-level
              // XTM Composer callbacks (connectorInstancesManagedByXtmComposer,
              // connectorInstanceByIdIgnoringTenantFilter):
              ConnectorInstanceService.class,
              // Background telemetry reader scoped via tenantTx.execute(TxCtx.allTenants()):
              InventoryMetricCollector.class,
              // Reads a connector instance to resolve inject test status; reached only from
              // TxCtx-carrying inject-test endpoints already reviewed for the injectors
              // activation (ScenarioInjectTestApi#testInject/#bulkTestInject,
              // SimulationInjectTestApi#testInject/#bulkTestInject):
              InjectTestStatusService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(ConnectorInstanceRepository.class)
          .because(
              "connector_instances is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule connector_instances_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Joins ManagerCreator#createManager's transaction, which sets
              // tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId)) before any
              // factory/migration runs; getInstances() therefore only sees this tenant's
              // already-migrated instance:
              ConfigurationMigration.class)
          .should()
          .callMethod(CatalogConnector.class, "getInstances")
          .because(
              "connector_instances is reached through CatalogConnector's association WITHOUT"
                  + " touching the repository: a lazy getInstances() call in an unscoped context"
                  + " silently sees zero rows. New callers must run inside a scoped transaction"
                  + " and be allowlisted here");

  static final ArchRule autonomous_runs_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Operator + orchestrator surface: TxCtx-carrying AutonomousRunApi (pinned by
              // TenantScopedEntrypointsTxCtxArchTest) plus scenario delete/bulk-delete:
              AutonomousRunService.class,
              // Isolated REQUIRES_NEW writer: takes its own TxCtx because REQUIRES_NEW suspends
              // the caller's GUC. Pinned by AutonomousRunReconciliationWriterTest:
              AutonomousRunReconciliationWriter.class,
              // Background watchdog: per-tenant primitive (forEachTenant + executeNew). Pinned
              // by AutonomousTimeoutService:
              AutonomousTimeoutService.class,
              // Simulation status changes look up whether the exercise is AI-driven
              // (existsBySimulationId). Driven by TxCtx-carrying ExerciseApi#changeExerciseStatus:
              ExerciseService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(AutonomousRunRepository.class)
          .because(
              "autonomous_runs is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule autonomous_events_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Timeline writer: tenant is stamped on every INSERT from the parent run. Callers
              // are AutonomousRunService (TxCtx-carrying HTTP + scoped timeout/reconcile):
              AutonomousEventService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(AutonomousEventRepository.class)
          .because(
              "autonomous_events is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule autonomous_directives_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Steering + winddown INSERTs stamp tenant from the parent run. Driven by
              // TxCtx-carrying AutonomousRunApi and the scoped timeout watchdog:
              AutonomousRunService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(AutonomousDirectiveRepository.class)
          .because(
              "autonomous_directives is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");
}
