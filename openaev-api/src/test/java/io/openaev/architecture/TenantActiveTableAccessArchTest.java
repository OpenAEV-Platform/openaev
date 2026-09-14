package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.custom_dashboard.CustomDashboardApiExporter;
import io.openaev.api.custom_dashboard.CustomDashboardApiImporter;
import io.openaev.api.notification.NotificationApi;
import io.openaev.api.notification_trigger.NotificationTriggerMapper;
import io.openaev.api.notifier.NotifierApi;
import io.openaev.api.xtmhub.XtmHubApi;
import io.openaev.database.model.Article;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Document;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Vulnerability;
import io.openaev.database.model.Widget;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.ChannelRepository;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.database.repository.CweRepository;
import io.openaev.database.repository.DomainRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.database.repository.LessonsTemplateRepository;
import io.openaev.database.repository.MitigationRepository;
import io.openaev.database.repository.NotificationEventRecordRepository;
import io.openaev.database.repository.NotificationRepository;
import io.openaev.database.repository.NotificationTriggerRepository;
import io.openaev.database.repository.NotifierRepository;
import io.openaev.database.repository.SecurityCoverageRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TagRuleRepository;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.database.repository.WidgetRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.database.repository.autonomous.AutonomousDirectiveRepository;
import io.openaev.database.repository.autonomous.AutonomousEventRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.engine.model.finding.FindingHandler;
import io.openaev.engine.model.securitydomain.SecurityDomainHandler;
import io.openaev.engine.model.vulnerableendpoint.VulnerableEndpointHandler;
import io.openaev.executors.Executor;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.caldera.service.CalderaExecutorContextService;
import io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorContextService;
import io.openaev.executors.mde.service.MdeExecutorContextService;
import io.openaev.executors.openaev.service.OpenAEVExecutorContextService;
import io.openaev.executors.paloaltocortex.service.PaloAltoCortexExecutorContextService;
import io.openaev.executors.sentinelone.service.SentinelOneExecutorContextService;
import io.openaev.executors.tanium.service.TaniumExecutorContextService;
import io.openaev.export.WorkflowExportInitializer;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.helper.InjectHelper;
import io.openaev.importer.V1_DataImporter;
import io.openaev.injectors.challenge.ChallengeExecutor;
import io.openaev.injectors.channel.ChannelExecutor;
import io.openaev.injectors.phishing.service.PhishingLandingPageService;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.injectors.challenge.ChallengeInjectorIntegration;
import io.openaev.integration.impl.injectors.challenge.ChallengeInjectorIntegrationFactory;
import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.notification.engine.NotificationDigestService;
import io.openaev.notification.engine.NotificationDispatchService;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationEventRetentionService;
import io.openaev.notification.engine.NotificationTriggerLoader;
import io.openaev.notification.engine.ResolvedNotificationTrigger;
import io.openaev.processor.core.V20260420_Migrate_rabbitmq_queues;
import io.openaev.processor.datapack.V20260101_Starter_pack;
import io.openaev.processor.datapack.V20260330_Default_tenant_data;
import io.openaev.processor.datapack.V20260708_Dynamic_injectors_base_url;
import io.openaev.rest.asset.security_platforms.SecurityPlatformApi;
import io.openaev.rest.atomic_testing.AtomicTestingApi;
import io.openaev.rest.attack_pattern.AttackPatternApi;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.challenge.ChallengeApi;
import io.openaev.rest.challenge.ScenarioChallengeApi;
import io.openaev.rest.challenge.SimulationChallengeApi;
import io.openaev.rest.channel.ChannelApi;
import io.openaev.rest.channel.output.ArticleOutput;
import io.openaev.rest.collector.CollectorApi;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.connector_instance.ConnectorInstanceApi;
import io.openaev.rest.custom_dashboard.CustomDashboardApi;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.custom_dashboard.CustomDashboardTenantService;
import io.openaev.rest.custom_dashboard.CustomDashboardWidgetApi;
import io.openaev.rest.custom_dashboard.WidgetService;
import io.openaev.rest.dashboard.DashboardApi;
import io.openaev.rest.dashboard.DashboardService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.domain.DomainApi;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.executor.ExecutorApi;
import io.openaev.rest.exercise.ExerciseApi;
import io.openaev.rest.exercise.ExerciseDashboardApi;
import io.openaev.rest.exercise.ExerciseImportApi;
import io.openaev.rest.exercise.exports.ExerciseFileExport;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.finding.FindingDistinctSearchService;
import io.openaev.rest.finding.FindingSearchApi;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.finding.FindingWriter;
import io.openaev.rest.inject.InjectApi;
import io.openaev.rest.inject.ScenarioInjectApi;
import io.openaev.rest.inject.SimulationInjectApi;
import io.openaev.rest.inject.exports.InjectsFileExport;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.ScenarioInjectService;
import io.openaev.rest.inject_expectation_trace.InjectExpectationTraceApi;
import io.openaev.rest.injector.InjectorApi;
import io.openaev.rest.injector_contract.InjectorContractApi;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.output.InjectorContractFullOutput;
import io.openaev.rest.kill_chain_phase.KillChainPhaseApi;
import io.openaev.rest.kill_chain_phase.KillChainPhaseInitializer;
import io.openaev.rest.kill_chain_phase.service.KillChainPhaseService;
import io.openaev.rest.lessons.ExerciseLessonsApi;
import io.openaev.rest.lessons.ScenarioLessonsApi;
import io.openaev.rest.lessons_template.LessonsTemplateApi;
import io.openaev.rest.mapper.MapperApi;
import io.openaev.rest.mitigation.MitigationApi;
import io.openaev.rest.payload.PayloadApi;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.payload.service.PayloadUpdateService;
import io.openaev.rest.payload.service.PayloadUpsertService;
import io.openaev.rest.scenario.ScenarioApi;
import io.openaev.rest.scenario.ScenarioDashboardApi;
import io.openaev.rest.scenario.ScenarioImportApi;
import io.openaev.rest.settings.TenantSettingsApi;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.scheduler.jobs.ComchecksExecutionJob;
import io.openaev.service.ChallengeService;
import io.openaev.service.ChannelService;
import io.openaev.service.EndpointService;
import io.openaev.service.EsAttackPathService;
import io.openaev.service.InjectExpectationTraceService;
import io.openaev.service.InjectImportService;
import io.openaev.service.InjectTestStatusService;
import io.openaev.service.InjectorService;
import io.openaev.service.MailingService;
import io.openaev.service.MapperService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.attackpath.AttackPathCausalSeedService;
import io.openaev.service.attackpath.AttackPathDeltaService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService;
import io.openaev.service.attackpath.ingestion.AttackPathFindingIngestionService;
import io.openaev.service.autonomous.AutonomousEventService;
import io.openaev.service.autonomous.AutonomousRunReconciliationWriter;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.autonomous.AutonomousTimeoutService;
import io.openaev.service.autonomous.CapabilityResolverService;
import io.openaev.service.chaining.ScopeSnapshotService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.ConnectorOrchestrationService;
import io.openaev.service.expectation.ChallengeBehavior;
import io.openaev.service.notification.NotificationService;
import io.openaev.service.notification.NotificationTriggerService;
import io.openaev.service.notification.NotifierService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.stix.SecurityCoverageService;
import io.openaev.service.targets.search.AgentTargetSearchAdaptor;
import io.openaev.service.threat_arsenal.ThreatArsenalImportService;
import io.openaev.telemetry.metric_collectors.InventoryMetricCollector;
import io.openaev.telemetry.metric_collectors.PlatformAdoptionMetricCollector;
import io.openaev.telemetry.metric_collectors.ProductInventoryMetricCollector;
import io.openaev.utils.ExpectationUtils;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.mapper.DocumentMapper;
import io.openaev.utils.mapper.FindingMapper;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.mapper.VulnerabilityMapper;
import io.openaev.xtmhub.XtmHubService;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.Query;

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
          "custom_dashboards",
          "cwes",
          "mitigations",
          "collectors",
          "executors",
          "injectors",
          "tags",
          "tag_rules",
          "channels",
          "domains",
          "attackpath_execution",
          "attackpath_finding",
          "secret_references",
          "secrets",
          "connector_instances",
          "autonomous_runs",
          "autonomous_events",
          "autonomous_directives",
          "kill_chain_phases",
          "security_coverages",
          "widgets",
          "tenant_xtmhub_registrations",
          "notifications",
          "challenges",
          "asset_groups",
          "findings",
          "assets",
          "notifiers",
          "notification_triggers",
          "notification_events");

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

    // Membership in GUARDED_TABLES is bookkeeping: it is satisfied by adding a string. What this
    // class actually promises is an accessor rule per table, and for several tables that rule was
    // never written - the string was added and the promise quietly lapsed. This asserts the rule
    // exists, and lists the tables that still owe one so the gap is visible rather than implied.
    //
    // TABLES_OWING_AN_ACCESSOR_RULE must only ever SHRINK. Adding a table to it to make a build
    // pass re-creates exactly the silence this check exists to end.
    Set<String> declaredRules =
        Arrays.stream(TenantActiveTableAccessArchTest.class.getDeclaredFields())
            .filter(f -> ArchRule.class.isAssignableFrom(f.getType()))
            .map(java.lang.reflect.Field::getName)
            .collect(Collectors.toSet());
    Set<String> missingRule =
        active.stream()
            .filter(t -> !TABLES_OWING_AN_ACCESSOR_RULE.contains(t))
            .filter(t -> declaredRules.stream().noneMatch(r -> r.startsWith(t + "_")))
            .collect(Collectors.toSet());
    assertTrue(
        missingRule.isEmpty(),
        "these active tables have no accessor rule in this class, so nothing constrains who reads"
            + " them: "
            + missingRule
            + ". Add a <table>_repository_access_is_reviewed rule with an explicit allowlist.");
  }

  /**
   * Active tables whose accessor rule has not been written yet, with the reason. Every entry is a
   * table where a new unscoped accessor would ship silently. Tracked in #7874.
   *
   * <p>Shrink-only. This list is a debt register, not an escape hatch.
   */
  private static final Set<String> TABLES_OWING_AN_ACCESSOR_RULE =
      Set.of(
          // 26 accessor classes across AssetRepository, EndpointRepository and
          // SecurityPlatformRepository - by far the widest surface of any activation so far. An
          // allowlist is only worth writing once each accessor's scope mechanism has been traced,
          // and waiving them wholesale would be the same silence this check is meant to remove.
          "assets",
          // Same, one lot earlier.
          "asset_groups",
          "secrets",
          "secret_references");

  /**
   * Repositories whose joined {@code @Query} methods have been reviewed for tenant correlation.
   * Other tenant-active repositories join this set as their queries are reviewed; the known backlog
   * is {@code AttackPathFindingRepository} (7 joined queries, none correlated).
   */
  private static final Set<Class<?>> REPOSITORIES_WITH_REVIEWED_JOINED_QUERIES =
      Set.of(
          CustomDashboardRepository.class,
          KillChainPhaseRepository.class,
          TenantXtmHubRegistrationRepository.class);

  @ArchTest
  static void joined_queries_on_active_tables_correlate_the_tenant(JavaClasses classes) {
    // A single-table @Query needs no predicate: the inspector's can_access_tenant is the whole
    // scoping story. A JOIN reaches rows the caller did not name, and the scope can be wider than
    // the tenant that owns them, so the query must correlate the two itself.
    List<String> uncorrelated = new ArrayList<>();
    for (Class<?> repository : REPOSITORIES_WITH_REVIEWED_JOINED_QUERIES) {
      for (Method method : repository.getDeclaredMethods()) {
        Query query = method.getAnnotation(Query.class);
        if (query == null) {
          continue;
        }
        String normalized = query.value().toLowerCase();
        if (normalized.contains(" join ") && !normalized.contains("tenant")) {
          uncorrelated.add(repository.getSimpleName() + "#" + method.getName());
        }
      }
    }
    assertTrue(
        uncorrelated.isEmpty(),
        "a joined @Query on a tenant-active table must correlate the tenant with the entity it is"
            + " filtered by, or the request scope alone decides which tenant's rows it reaches:"
            + " "
            + uncorrelated);
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
  static final ArchRule tenant_xtmhub_registrations_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying XTM Hub API entrypoints, pinned by
              // TenantScopedEntrypointsTxCtxArchTest:
              XtmHubApi.class,
              // Service behind the scoped handlers and the scoped background refresh:
              XtmHubService.class,
              // Background telemetry reader scoped via tenantTx.execute(TxCtx.allTenants()):
              PlatformAdoptionMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(TenantXtmHubRegistrationRepository.class)
          .because(
              "tenant_xtmhub_registrations is tenant-active: an accessor without a tenant scope"
                  + " silently reads zero rows. New accessors must carry a scope and be"
                  + " allowlisted here");

  @ArchTest
  static final ArchRule tags_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              io.openaev.rest.tag.TagApi.class,
              io.openaev.rest.scenario.ScenarioApi.class,
              io.openaev.rest.exercise.ExerciseApi.class,
              io.openaev.rest.team.TeamApi.class,
              io.openaev.rest.user.PlayerApi.class,
              io.openaev.rest.organization.OrganizationApi.class,
              io.openaev.rest.asset_group.AssetGroupApi.class,
              io.openaev.rest.document.DocumentApi.class,
              io.openaev.rest.challenge.ChallengeApi.class,
              io.openaev.api.chaining.ChainingApi.class,
              io.openaev.rest.asset.ai_targets.AiTargetApi.class,
              io.openaev.rest.asset.security_platforms.SecurityPlatformApi.class,
              // Services behind the entrypoints above and import/export paths using explicit
              // tenant-scoped tag lookups:
              io.openaev.rest.tag.TagService.class,
              io.openaev.rest.inject.service.InjectService.class,
              io.openaev.rest.injector_contract.InjectorContractService.class,
              io.openaev.service.scenario.ScenarioService.class,
              io.openaev.rest.user.PlayerService.class,
              io.openaev.service.EndpointService.class,
              io.openaev.rest.payload.service.PayloadCreationService.class,
              io.openaev.rest.payload.service.PayloadUpdateService.class,
              io.openaev.service.AtomicTestingService.class,
              io.openaev.service.TagRuleService.class,
              io.openaev.service.UserService.class,
              io.openaev.service.credential.CredentialService.class,
              io.openaev.rest.document.DocumentService.class,
              io.openaev.service.ScenarioToExerciseService.class,
              // Import path with explicit tenant remapping semantics:
              io.openaev.importer.V1_DataImporter.class,
              // Background indexing fetch path:
              io.openaev.engine.model.tag.TagHandler.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(TagRepository.class)
          .because(
              "tags is tenant-active: an accessor without a tenant scope silently reads zero rows."
                  + " New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule tag_rules_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Sole class depending on TagRuleRepository. Every entrypoint is either a
              // TxCtx-carrying handler (TagRuleApi, pinned by
              // TenantScopedEntrypointsTxCtxArchTest) or a tenant-scoped datapack/service call
              // (ensurePresetRules resolves the write tenant via TenantWriteScopeResolver; reads
              // through ExerciseService/ScenarioService/InjectService/InjectExecutionStep run
              // inside their own TxCtx-carrying entrypoints):
              io.openaev.service.TagRuleService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(TagRuleRepository.class)
          .because(
              "tag_rules is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule domains_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              DomainApi.class,
              // Domain API/service write tenant attribution and read scope.
              DomainService.class,
              // Payload update path uses domainRepository.findAllById and is called only by
              // TxCtx-carrying handlers (PayloadApi / ThreatArsenalApi) already pinned by
              // TenantScopedEntrypointsTxCtxArchTest.
              PayloadUpdateService.class,
              // Background indexing reader, documented degraded path when no background scope is
              // set.
              SecurityDomainHandler.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(DomainRepository.class)
          .because(
              "domains is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule notifications_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying notification self-service handlers.
              NotificationApi.class,
              // Service behind NotificationApi; ownership predicates and bulk scopes are resolved
              // here, then scoped by the entrypoint transaction.
              NotificationService.class,
              // Notification writes from the engine and notifier test dispatch path are explicit
              // INSERTs with tenant attribution before save.
              NotificationDispatchService.class,
              // The notifier test endpoint is the API entrypoint to the dispatch path and carries
              // TxCtx (pinned by TenantScopedEntrypointsTxCtxArchTest).
              NotifierApi.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(NotificationRepository.class)
          .because(
              "notifications is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule notifiers_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying notifier handlers, pinned by TenantScopedEntrypointsTxCtxArchTest.
              NotifierApi.class,
              // Service behind NotifierApi: reads are scoped by the entrypoint transaction, creates
              // are attributed through TenantWriteScopeResolver, and the built-in backfill runs
              // only under a single-tenant scope.
              NotifierService.class,
              // Resolves a trigger's notifier ids inside the TxCtx-scoped NotificationTriggerApi
              // transaction; the inspector scopes the lookup.
              NotificationTriggerMapper.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(NotifierRepository.class)
          .because(
              "notifiers is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule notifiers_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Resolves the association inside the API transaction to build the output DTO.
              NotificationTriggerMapper.class,
              // Validates that a trigger has at least one notifier, inside its own transaction.
              NotificationTriggerService.class,
              // Detaches the association into ResolvedNotifier records; runs under the
              // allTenants() scope NotificationTriggerLoader opens (#7864).
              ResolvedNotificationTrigger.class,
              NotificationTriggerLoader.class)
          .should()
          .callMethod(NotificationTrigger.class, "getNotifiers")
          .because(
              "notifiers is reached through NotificationTrigger's lazy @ManyToMany WITHOUT touching"
                  + " the repository: resolving it in an unscoped context silently yields an empty"
                  + " notifier list, and the dispatch pipeline then delivers nothing at all. New"
                  + " callers must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule channels_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              ChannelApi.class,
              // Service behind the ChannelApi handlers and the scenario/exercise channel lists:
              ChannelService.class,
              // HTTP-triggered importer; resolves the write tenant explicitly before reusing or
              // creating a channel.
              V1_DataImporter.class,
              // Documented degraded background reader (telemetry counts read 0 rows unscoped):
              ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(ChannelRepository.class)
          .because(
              "channels is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule channels_article_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Derived getArticlesForChannel helpers on the owning aggregates:
              Exercise.class,
              Scenario.class,
              // Mapped inside the scoped transaction of the calling handlers:
              ArticleOutput.class,
              ChannelService.class,
              // Scenario/exercise copy flows read the channel association while carrying the source
              // tenant scope, not from an unscoped serializer.
              ExerciseService.class,
              ScenarioService.class,
              ScenarioToExerciseService.class,
              // Background inject execution resolves article URLs under TenantScopedJobRunner.
              ChannelExecutor.class,
              // Export builders read the association inside TxCtx-scoped export handlers.
              ExerciseFileExport.class,
              InjectsFileExport.class)
          .should()
          .callMethod(Article.class, "getChannel")
          .because(
              "channels is reached through Article#getChannel without touching ChannelRepository."
                  + " A lazy association load outside a scoped transaction silently resolves no"
                  + " channel. New callers must run inside a scoped transaction and be allowlisted"
                  + " here");

  @ArchTest
  static final ArchRule channels_document_logo_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Relations DTO is built inside DocumentApi#getDocumentRelations' scoped
              // transaction.
              DocumentMapper.class)
          .should()
          .callMethod(Document.class, "getChannelsByLogoDark")
          .orShould()
          .callMethod(Document.class, "getChannelsByLogoLight")
          .because(
              "channels is also reached through Document's inverse logo associations without"
                  + " touching ChannelRepository. New callers must build those relations inside a"
                  + " scoped transaction and be allowlisted here");

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
  //    tenantScopedJobRunner.runInTenant(tenantId, work) ->
  //    tenantTx.execute(TxCtx.forTenant(tenantId), work).
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
  static final ArchRule custom_dashboards_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // CRUD, option lookups and import/export are driven by TxCtx-carrying dashboard
              // endpoints (pinned by TenantScopedEntrypointsTxCtxArchTest and the custom
              // dashboard/widget isolation tests):
              CustomDashboardApi.class,
              CustomDashboardWidgetApi.class,
              CustomDashboardApiImporter.class,
              CustomDashboardApiExporter.class,
              CustomDashboardService.class,
              WidgetService.class,
              // Home-dashboard reads resolve and initialize the widget list inside TxCtx-scoped
              // transactions (pinned by TenantScopedEntrypointsTxCtxArchTest):
              CustomDashboardTenantService.class,
              TenantSettingsApi.class,
              ExerciseDashboardApi.class,
              ScenarioDashboardApi.class,
              // Startup datapack import explicitly attributes rows to the tenant before save.
              V20260101_Starter_pack.class,
              // Background telemetry must read all tenants explicitly once the table is active.
              ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(CustomDashboardRepository.class)
          .because(
              "custom_dashboards is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must run inside a reviewed scope and be"
                  + " allowlisted here");

  @ArchTest
  static final ArchRule widgets_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // CRUD endpoints carry TxCtx and delegate through the service (pinned by
              // TenantScopedEntrypointsTxCtxArchTest and CustomDashboardWidgetHttpIsolationTest):
              CustomDashboardWidgetApi.class,
              WidgetService.class,
              // Dashboard widgets dereference the owning custom dashboard inside TxCtx-scoped
              // requests (pinned by DashboardApiTenantScopeTest):
              DashboardApi.class,
              DashboardService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(WidgetRepository.class)
          .because(
              "widgets is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must run inside a reviewed scope and be allowlisted"
                  + " here");

  @ArchTest
  static final ArchRule custom_dashboards_widgets_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // These services initialize the LAZY widget collection inside the request-scoped
              // transaction before open-in-view serialization (pinned by the custom dashboard
              // isolation tests and TenantScopedEntrypointsTxCtxArchTest):
              CustomDashboardService.class, CustomDashboardTenantService.class)
          .should()
          .callMethod(CustomDashboard.class, "getWidgets")
          .because(
              "custom_dashboards serializes its widget list through a LAZY association. New"
                  + " callers must initialize it inside a tenant-scoped transaction and be"
                  + " allowlisted here so open-in-view cannot silently return an empty list");

  @ArchTest
  static final ArchRule widgets_dashboard_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Owning entity itself: Lombok-generated methods may call its own getter; this is not
              // an external accessor path.
              Widget.class,
              // DashboardService resolves the widget and then dereferences its owning dashboard
              // inside TxCtx-scoped endpoints (pinned by DashboardApiTenantScopeTest and
              // TenantScopedEntrypointsTxCtxArchTest):
              DashboardService.class)
          .should()
          .callMethod(Widget.class, "getCustomDashboard")
          .because(
              "widgets is tenant-active and DashboardService reaches custom_dashboards through"
                  + " Widget#getCustomDashboard. New callers must run inside a tenant-scoped"
                  + " transaction and be allowlisted here");

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
  static final ArchRule findings_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // HTTP read/write paths: every entrypoint carries a TxCtx and the aspect scopes the
              // transaction (pinned by TenantScopedEntrypointsTxCtxArchTest).
              FindingSearchApi.class,
              FindingService.class,
              FindingDistinctSearchService.class,
              // Reached only from those handlers, inside their scoped transaction.
              FindingMapper.class,
              // REQUIRES_NEW, so it declares its own TxCtx and the aspect scopes the new
              // transaction (see the note on the class).
              FindingWriter.class,
              // Background writers, each reaching the primitive with the tenant taken from the
              // inject or run that owns the work.
              AttackPathExecutionIngestionService.class,
              AttackPathFindingIngestionService.class,
              AttackPathGraphService.class,
              AutonomousRunService.class,
              // ES indexing sweep: EngineSyncExecutionJob opens TxCtx.allTenants() before calling
              // either handler.
              FindingHandler.class,
              VulnerableEndpointHandler.class,
              // Telemetry gauge, explicitly TxCtx.allTenants() (ProductInventoryTenantScopeTest).
              ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(FindingRepository.class)
          .because(
              "findings is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule security_coverages_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Coverage upsert/read logic; caller transaction scope is pinned at API entrypoint by
              // TenantScopedEntrypointsTxCtxArchTest.
              SecurityCoverageService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(SecurityCoverageRepository.class)
          .because(
              "security_coverages is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule security_coverages_exercise_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Own service; every public entrypoint is TxCtx-scoped, pinned by
              // TenantScopedEntrypointsTxCtxArchTest:
              SecurityCoverageService.class,
              // Reads exercise.getSecurityCoverage() in shouldCreateCoverageSendJob, reached only
              // from TxCtx-carrying HTTP entrypoints (ExpectationApi#deleteInjectExpectationResult,
              // ChallengeApi#tryChallenge, SimulationChallengeApi#validateChallenge,
              // InjectApi#injectExecutionCallback) and from already-scoped background jobs
              // (SecurityCoverageJob, InjectsFinalizationJob#handleAutoClosingSimulations), all
              // pinned
              // by SecurityCoverageTenantScopeTest#SendJobCreationGateRequiresScope:
              SecurityCoverageSendJobService.class)
          .should()
          .callMethod(Exercise.class, "getSecurityCoverage")
          .because(
              "security_coverages is reached through Exercise's association WITHOUT touching the"
                  + " repository: a lazy getSecurityCoverage() in an unscoped context silently"
                  + " reads null. New callers must run inside a scoped transaction and be"
                  + " allowlisted here");

  @ArchTest
  static final ArchRule security_coverages_scenario_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Own service; every public entrypoint is TxCtx-scoped, pinned by
              // TenantScopedEntrypointsTxCtxArchTest:
              SecurityCoverageService.class,
              // Copies the scenario's coverage onto the new exercise during scenario->exercise
              // promotion; callers (ScenarioApi, AutonomousRunService) already carry TxCtx,
              // verified in the Phase 3 audit:
              ScenarioToExerciseService.class)
          .should()
          .callMethod(Scenario.class, "getSecurityCoverage")
          .because(
              "security_coverages is reached through Scenario's association WITHOUT touching the"
                  + " repository: a lazy getSecurityCoverage() in an unscoped context silently"
                  + " reads null. New callers must run inside a scoped transaction and be"
                  + " allowlisted here");

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
  static final ArchRule kill_chain_phases_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              KillChainPhaseApi.class,
              AttackPatternApi.class,
              ExerciseApi.class,
              // Sole write path for the table (endpoints and the scenario/simulation import):
              // resolves the write tenant explicitly and looks rows up by per-tenant predicates:
              KillChainPhaseService.class,
              // Reads phases by id for an upserted attack pattern; driven by the TxCtx-carrying
              // AttackPatternApi#upsertAttackPatterns:
              AttackPatternService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(KillChainPhaseRepository.class)
          .because(
              "kill_chain_phases is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule kill_chain_phases_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Hydrate the association inside the TxCtx-scoped transaction, before the
              // open-in-view JSON rendering (the #7025 blind spot applied to this table):
              KillChainPhaseInitializer.class,
              AttackPatternApi.class,
              // Lombok's @Data toString() on the owning entity reads every getter, including this
              // one. It never renders a response, so it cannot leak or go dark:
              AttackPattern.class,
              // Derived *_kill_chain_phases getters on the aggregates themselves:
              Exercise.class,
              Inject.class,
              InjectorContract.class,
              Scenario.class,
              // Map or hydrate phases inside the scoped transactions of wired handlers:
              InjectMapper.class,
              InjectHelper.class,
              InjectService.class,
              ScenarioService.class,
              AttackPatternService.class,
              WorkflowExportInitializer.class,
              EsAttackPathService.class,
              V1_DataImporter.class)
          .should()
          .callMethod(AttackPattern.class, "getKillChainPhases")
          .because(
              "kill_chain_phases is reached through AttackPattern's LAZY @ManyToMany WITHOUT"
                  + " touching the repository. The tenant scope is transaction-local and"
                  + " open-in-view renders after the commit, so a lazy load at rendering time"
                  + " silently serializes an EMPTY phase list. New callers must run inside a scoped"
                  + " transaction and be allowlisted here");

  @ArchTest
  static final ArchRule challenges_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              ChallengeApi.class,
              ScenarioChallengeApi.class,
              SimulationChallengeApi.class,
              // Reads only (tryChallenge, enrichment lookups), driven by the TxCtx-carrying
              // entrypoints above:
              ChallengeService.class,
              // Execution-engine background path: already scoped by TenantScopedJobRunner, which
              // opens the tenant transaction InjectsExecutionJob runs every inject execution
              // under, independently of the TxCtx/@Transactional aspect:
              ChallengeExecutor.class,
              // Expectation expansion: resolves the challenges referenced by the inject content
              // (findAllById on IDs already scoped to this inject) while building
              // ChallengeInjectExpectation entries during the same inject-execution flow as
              // ChallengeExecutor above, so it runs under the same scoped transaction:
              ChallengeBehavior.class,
              // Import path: resolves the write tenant explicitly and looks rows up by the
              // per-tenant business-key predicate before create:
              V1_DataImporter.class,
              // Wiring-only dependencies: they pass the repository through to ChallengeExecutor,
              // whose execution path is already scoped (allowlisted above).
              ChallengeInjectorIntegration.class,
              ChallengeInjectorIntegrationFactory.class,
              // Documents path resolves challenge documents under TxCtx-carrying APIs.
              DocumentService.class,
              // Platform-wide telemetry counter, intentionally unscoped (documented degradation):
              // once challenges is active it counts only the caller's tenant, not the platform
              // total. Tracked as an accepted limitation, not a blocker.
              ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(ChallengeRepository.class)
          .because(
              "challenges is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule challenges_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Renders the response inside the scoped transaction of the wired handler
              // (DocumentApi#getDocumentRelations, which already carries TxCtx):
              DocumentMapper.class)
          .should()
          .callMethod(Document.class, "getChallenges")
          .because(
              "challenges is reached through Document's association WITHOUT touching the"
                  + " repository: a lazy getChallenges() in an unscoped context silently loads"
                  + " zero rows. New callers must run inside a scoped transaction and be"
                  + " allowlisted here");

  @ArchTest
  static final ArchRule notification_triggers_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Service behind NotificationTriggerApi, whose five handlers carry TxCtx (pinned by
              // TenantScopedEntrypointsTxCtxArchTest).
              NotificationTriggerService.class,
              // Resolves notifiers and child triggers for the same handlers; child lookups are
              // constrained to the resolved write tenant, not to the request's read scope.
              NotificationTriggerMapper.class,
              // Cross-tenant engine loader: reads every tenant's triggers under allTenants().
              NotificationTriggerLoader.class,
              // Platform-wide telemetry gauge, scoped with countAcrossAllTenants().
              ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(NotificationTriggerRepository.class)
          .because(
              "notification_triggers is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule notification_events_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Outbox writer: saves inside tenantTx.execute(forTenant(trigger tenant)) with the
              // tenant stamped on every row before save.
              NotificationEngineService.class,
              // Cross-tenant digest window read, under allTenants().
              NotificationTriggerLoader.class,
              // Bulk purge by predicate, under allTenants().
              NotificationEventRetentionService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(NotificationEventRecordRepository.class)
          .because(
              "notification_events is tenant-active: an unscoped read returns nothing and an"
                  + " unscoped DELETE purges nothing while reporting success. New accessors must"
                  + " carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule notification_triggers_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Detached view built inside the loader's cross-tenant transaction.
              ResolvedNotificationTrigger.class,
              // Maps the composed children to ids inside the handler's scoped transaction.
              NotificationTriggerMapper.class,
              // Validates that a digest composes at least one child, same transaction.
              NotificationTriggerService.class,
              // Reads the event's trigger name inside the allTenants() digest transaction.
              NotificationDigestService.class,
              NotificationTriggerLoader.class)
          .should()
          .callMethod(NotificationTrigger.class, "getChildTriggers")
          .orShould()
          .callMethod(NotificationEventRecord.class, "getTrigger")
          .because(
              "notification_triggers is also reached without touching its repository: through the"
                  + " self-referencing childTriggers collection and through"
                  + " NotificationEventRecord#getTrigger, a required @ManyToOne with no @NotFound"
                  + " that THROWS EntityNotFoundException when its target is out of scope rather"
                  + " than degrading to null. New callers must run inside a scoped transaction"
                  + " wide enough to see the target, and be allowlisted here");

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
