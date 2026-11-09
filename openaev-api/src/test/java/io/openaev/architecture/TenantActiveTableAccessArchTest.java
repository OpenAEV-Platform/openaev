package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.api.asset.AssetMarkingsService;
import io.openaev.api.chaining.ActionStep;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.custom_dashboard.CustomDashboardApiExporter;
import io.openaev.api.custom_dashboard.CustomDashboardApiImporter;
import io.openaev.api.custom_domain.CustomDomainService;
import io.openaev.api.detection_remediation.DetectionRemediationApi;
import io.openaev.api.marking_definition.MarkingDefinitionApi;
import io.openaev.api.notification.NotificationApi;
import io.openaev.api.notification_trigger.NotificationTriggerMapper;
import io.openaev.api.notifier.NotifierApi;
import io.openaev.api.payload.PayloadApiExporter;
import io.openaev.api.threat_arsenal.ThreatArsenalApi;
import io.openaev.api.threat_arsenal.ThreatArsenalApiExporter;
import io.openaev.api.xtmhub.XtmHubApi;
import io.openaev.config.TenantUriUtils;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Article;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Document;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Vulnerability;
import io.openaev.database.model.Widget;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.payload.HasPayloadIdentity;
import io.openaev.database.model.payload.PayloadDeleteGuard;
import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.CatalogItemRepository;
import io.openaev.database.repository.CatalogReleaseRepository;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.ChannelRepository;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.database.repository.CustomDomainRepository;
import io.openaev.database.repository.CweRepository;
import io.openaev.database.repository.DataPackRepository;
import io.openaev.database.repository.DomainRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.database.repository.LessonsTemplateRepository;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.database.repository.MitigationRepository;
import io.openaev.database.repository.NotificationEventRecordRepository;
import io.openaev.database.repository.NotificationRepository;
import io.openaev.database.repository.NotificationTriggerRepository;
import io.openaev.database.repository.NotifierRepository;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.database.repository.PhishingResultRepository;
import io.openaev.database.repository.ReportingGenerationRepository;
import io.openaev.database.repository.ReportingRepository;
import io.openaev.database.repository.ReportingScheduleRepository;
import io.openaev.database.repository.ScopeVariableRepository;
import io.openaev.database.repository.SecurityCoverageRepository;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TagRuleRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.database.repository.VulnerabilityRepository;
import io.openaev.database.repository.WidgetRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.database.repository.WorkflowStateRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionCollectorRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRemediationRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.database.repository.autonomous.AutonomousDirectiveRepository;
import io.openaev.database.repository.autonomous.AutonomousEventRepository;
import io.openaev.database.repository.autonomous.AutonomousObjectiveTemplateRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.engine.model.attackpattern.AttackPatternHandler;
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
import io.openaev.executors.utils.ExecutorUtils;
import io.openaev.export.WorkflowExportInitializer;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.helper.InjectHelper;
import io.openaev.importer.V1_DataImporter;
import io.openaev.injectors.challenge.ChallengeExecutor;
import io.openaev.injectors.channel.ChannelExecutor;
import io.openaev.injectors.phishing.PhishingExecutor;
import io.openaev.injectors.phishing.service.PhishingEmailTemplateService;
import io.openaev.injectors.phishing.service.PhishingLandingPageService;
import io.openaev.injectors.phishing.service.PhishingTrackingPublicLookupService;
import io.openaev.injectors.phishing.service.PhishingTrackingService;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.executors.openaev.OpenAEVExecutorIntegration;
import io.openaev.integration.impl.executors.openaev.OpenAEVExecutorIntegrationFactory;
import io.openaev.integration.impl.injectors.challenge.ChallengeInjectorIntegration;
import io.openaev.integration.impl.injectors.challenge.ChallengeInjectorIntegrationFactory;
import io.openaev.integration.impl.injectors.phishing.PhishingInjectorIntegration;
import io.openaev.integration.impl.injectors.phishing.PhishingInjectorIntegrationFactory;
import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.notification.engine.NotificationDigestService;
import io.openaev.notification.engine.NotificationDispatchService;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationEventRetentionService;
import io.openaev.notification.engine.NotificationTriggerLoader;
import io.openaev.notification.engine.ResolvedNotificationTrigger;
import io.openaev.processor.MigrationProcessor;
import io.openaev.processor.Processable;
import io.openaev.processor.core.V20260420_Migrate_rabbitmq_queues;
import io.openaev.processor.core.V20260725_Fix_starter_pack_payload_contracts;
import io.openaev.processor.datapack.V20260101_Starter_pack;
import io.openaev.processor.datapack.V20260330_Default_tenant_data;
import io.openaev.processor.datapack.V20260708_Dynamic_injectors_base_url;
import io.openaev.processor.datapack.V20260914_Default_tenant_markings;
import io.openaev.processor.datapack.V20261002_Default_group_markings;
import io.openaev.rest.asset.endpoint.EndpointApi;
import io.openaev.rest.asset.security_platforms.SecurityPlatformApi;
import io.openaev.rest.atomic_testing.AtomicTestingApi;
import io.openaev.rest.attack_pattern.AttackPatternApi;
import io.openaev.rest.attack_pattern.AttackPatternInitializer;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.attack_pattern.service.AttackPatternUpsertService;
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
import io.openaev.rest.inject.service.ExecutableInjectService;
import io.openaev.rest.inject.service.InjectExecutionService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
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
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.payload.service.PayloadUpdateService;
import io.openaev.rest.payload.service.PayloadUpsertService;
import io.openaev.rest.reporting.ReportingApi;
import io.openaev.rest.reporting.ReportingService;
import io.openaev.rest.reporting.service.PlaywrightReportingRenderer;
import io.openaev.rest.reporting.service.ReportingScheduleLoader;
import io.openaev.rest.reporting.service.ReportingScheduleService;
import io.openaev.rest.scenario.ScenarioApi;
import io.openaev.rest.scenario.ScenarioDashboardApi;
import io.openaev.rest.scenario.ScenarioImportApi;
import io.openaev.rest.settings.TenantSettingsApi;
import io.openaev.rest.team.TeamApi;
import io.openaev.rest.user.MeApi;
import io.openaev.rest.user.PlayerApi;
import io.openaev.rest.user.PlayerService;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.scheduler.jobs.AgentInactivityMonitorJob;
import io.openaev.scheduler.jobs.ComchecksExecutionJob;
import io.openaev.search.FullTextSearchService;
import io.openaev.service.AgentService;
import io.openaev.service.ChallengeService;
import io.openaev.service.ChannelService;
import io.openaev.service.DataPackService;
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
import io.openaev.service.TenantGroupService;
import io.openaev.service.attackpath.AttackPathCausalSeedService;
import io.openaev.service.attackpath.AttackPathDeltaService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.AttackPathSecurityPlatformResolver;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService;
import io.openaev.service.attackpath.ingestion.AttackPathFindingIngestionService;
import io.openaev.service.autonomous.AutonomousEventService;
import io.openaev.service.autonomous.AutonomousObjectiveTemplateService;
import io.openaev.service.autonomous.AutonomousRunReconciliationWriter;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.autonomous.AutonomousTimeoutService;
import io.openaev.service.autonomous.CapabilityResolverService;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.CatalogService;
import io.openaev.service.chaining.ScopeSnapshotService;
import io.openaev.service.chaining.StepEventService;
import io.openaev.service.chaining.WorkflowEndService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.ConnectorOrchestrationService;
import io.openaev.service.custom_domain.CustomDomainPublicLookupService;
import io.openaev.service.expectation.ChallengeBehavior;
import io.openaev.service.finding.FindingValueDisplayBackfill;
import io.openaev.service.inject.BatchingInjectStatusService;
import io.openaev.service.marking_definition.MarkingDefinitionService;
import io.openaev.service.notification.NotificationService;
import io.openaev.service.notification.NotificationTriggerService;
import io.openaev.service.notification.NotifierService;
import io.openaev.service.organization.OrganizationService;
import io.openaev.service.phishing.PhishingLandingPagePublicLookupService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.stix.SecurityCoverageService;
import io.openaev.service.targets.search.AgentTargetSearchAdaptor;
import io.openaev.service.threat_arsenal.ThreatArsenalImportService;
import io.openaev.service.threat_arsenal.ThreatArsenalService;
import io.openaev.telemetry.metric_collectors.CredentialMetricCollector;
import io.openaev.telemetry.metric_collectors.InventoryMetricCollector;
import io.openaev.telemetry.metric_collectors.PlatformAdoptionMetricCollector;
import io.openaev.telemetry.metric_collectors.ProductInventoryMetricCollector;
import io.openaev.utils.ExpectationUtils;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.mapper.DocumentMapper;
import io.openaev.utils.mapper.FindingMapper;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.mapper.PayloadMapper;
import io.openaev.utils.mapper.ThreatArsenalMapper;
import io.openaev.utils.mapper.VulnerabilityMapper;
import io.openaev.xtmhub.XtmHubService;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.RequestMapping;

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
          "documents",
          "cwes",
          "mitigations",
          "collectors",
          "executors",
          "injectors",
          "tags",
          "tag_rules",
          "organizations",
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
          "marking_definitions",
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
          "notification_events",
          "custom_domains",
          "collector_types",
          "phishing_email_templates",
          "phishing_landing_pages",
          "attackpath_execution_collector",
          "attackpath_execution_remediation",
          "asset_agent_jobs",
          "payloads",
          "vulnerabilities",
          "phishing_results",
          "reporting_schedules",
          "reportings",
          "reporting_generations",
          "datapacks",
          "teams",
          "attack_patterns",
          "agents",
          "autonomous_objective_templates",
          "catalog_items",
          "catalog_releases",
          "parameters",
          "payload_identities",
          "workflows",
          "steps",
          "conditions",
          "scope_variables",
          "steps_delay_queue",
          "workflow_scope_rules",
          "workflow_states",
          "injectors_contracts",
          "injector_contract_tags",
          "injectors_contracts_attack_patterns",
          "injectors_contracts_domains",
          "injectors_contracts_vulnerabilities",
          "injectors_injector_contracts");

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
        tablesWithoutAccessorRule(
            active, declaredRules, TABLES_OWING_AN_ACCESSOR_RULE, TABLES_GUARDED_BY_OTHER_RULES);
    assertTrue(
        missingRule.isEmpty(),
        "these active tables have no accessor rule in this class, so nothing constrains who reads"
            + " them: "
            + missingRule
            + ". Add a <table>_repository_access_is_reviewed rule with an explicit allowlist.");
  }

  /**
   * The active tables that have no {@code <table>_repository_access_is_reviewed} rule among {@code
   * declaredRules} and are not in {@code owing}. The rule name is matched in full: a table name is
   * often the prefix of another one ({@code steps} and {@code steps_delay_queue}), and a prefix
   * match would let the longer table's rule stand in for the shorter table's missing one. A table
   * of {@code guardedByOtherRules} counts as guarded only when every rule named for it is declared.
   */
  static Set<String> tablesWithoutAccessorRule(
      Set<String> active,
      Set<String> declaredRules,
      Set<String> owing,
      Map<String, Set<String>> guardedByOtherRules) {
    return active.stream()
        .filter(t -> !owing.contains(t))
        .filter(t -> !declaredRules.contains(t + "_repository_access_is_reviewed"))
        .filter(
            t ->
                !guardedByOtherRules.containsKey(t)
                    || !declaredRules.containsAll(guardedByOtherRules.get(t)))
        .collect(Collectors.toSet());
  }

  /**
   * Active tables guarded by rules other than a repository access rule, with those rules. {@code
   * parameters} is dual-scope: a platform row is readable under any scope, so its guard follows its
   * writers and the readers of its tenant rows instead of its repository.
   */
  private static final Map<String, Set<String>> TABLES_GUARDED_BY_OTHER_RULES =
      Map.of(
          "parameters",
          Set.of(
              "parameters_platform_row_writers_run_under_platform_write",
              "parameters_tenant_row_readers_off_the_request_thread"));

  @ArchTest
  static void the_accessor_rule_of_a_table_is_matched_by_its_full_name(JavaClasses classes) {
    // Each active table below is the prefix of another one whose rule exists; neither borrowed
    // rule may count as its own.
    assertEquals(
        Set.of("steps", "attackpath_execution"),
        tablesWithoutAccessorRule(
            Set.of(
                "steps",
                "steps_delay_queue",
                "attackpath_execution",
                "attackpath_execution_collector"),
            Set.of(
                "steps_delay_queue_repository_access_is_reviewed",
                "attackpath_execution_collector_repository_access_is_reviewed"),
            Set.of(),
            Map.of()));
  }

  @ArchTest
  static void a_table_guarded_by_other_rules_counts_only_when_each_of_them_exists(
      JavaClasses classes) {
    Map<String, Set<String>> guardedByOtherRules =
        Map.of("parameters", Set.of("parameters_writers", "parameters_readers"));
    assertEquals(
        Set.of(),
        tablesWithoutAccessorRule(
            Set.of("parameters"),
            Set.of("parameters_writers", "parameters_readers"),
            Set.of(),
            guardedByOtherRules));
    assertEquals(
        Set.of("parameters"),
        tablesWithoutAccessorRule(
            Set.of("parameters"), Set.of("parameters_writers"), Set.of(), guardedByOtherRules));
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
          TenantXtmHubRegistrationRepository.class,
          ReportingScheduleRepository.class);

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
  static final ArchRule marking_definitions_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoint, pinned by TenantScopedEntrypointsTxCtxArchTest:
              MarkingDefinitionApi.class,
              // Service behind the handler; every caller is a wired handler:
              MarkingDefinitionService.class,
              // Provisioning datapack: seeds protected defaults during tenant creation.
              V20260330_Default_tenant_data.class,
              // Provisioning datapack: seeds the default TLP markings. Runs under the same
              // tenant-scoped transaction primitive as the datapack above (MigrationProcessor ->
              // tenantTx.execute/setScopeOnCurrentTransaction with TxCtx.forTenant(...)), so it
              // needs no waiver.
              V20260914_Default_tenant_markings.class,
              // Provisioning datapack: grants each default group (Admin/Manager/Observer) the TLP
              // marking matching its role. Runs under the same tenant-scoped transaction primitive
              // as the two datapacks above (MigrationProcessor -> tenantTx.execute/
              // setScopeOnCurrentTransaction with TxCtx.forTenant(...)), so it needs no waiver.
              V20261002_Default_group_markings.class,
              // Resolves the markings a group is being told to grant. Runs on the HTTP path inside
              // a @Transactional method that takes a TxCtx, so app.current_tenants is already set
              // and the inspector rewrites the read with can_access_tenant: a marking id belonging
              // to another tenant does not come back, and the size mismatch becomes a 404 rather
              // than a silent partial assignment.
              //
              // 🔴 The inspector is NOT relied on alone here. In the configuration
              // TenantGroupMarkingsApiTest actually exercises, marking_definitions is left ungated
              // and the escalation guard alone refuses the foreign marking; under the table's real,
              // unconditionally-active configuration the inspector also rewrites this same
              // findAllById, and the foreign row never comes back before the escalation guard is
              // reached. Either way the assignment is refused, since a clearance is per tenant. See
              // that test's own comment for the confirmed behavior under both configurations.
              // Pinned by TenantGroupMarkingsApiTest:
              TenantGroupService.class,
              // Resolves the markings an asset is being labelled with. Same shape as
              // TenantGroupService above: HTTP path, inside a @Transactional method taking a TxCtx,
              // so app.current_tenants is set and the read is rewritten with can_access_tenant —
              // another tenant's marking id does not come back and the size mismatch becomes a 404.
              // The escalation guard is again the independent check, for the L1-cache reason
              // spelled out above. Pinned by AssetMarkingsApiTest:
              AssetMarkingsService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(MarkingDefinitionRepository.class)
          .because(
              "marking_definitions is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule documents_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying document endpoints and the service behind them (pinned by
              // TenantScopedEntrypointsTxCtxArchTest and DocumentHttpIsolationTest):
              io.openaev.rest.document.DocumentApi.class,
              io.openaev.rest.document.DocumentService.class,
              // Other TxCtx-carrying endpoints that resolve documents through their own aggregate
              // (challenge documents, article/channel documents, exercise attachments, security
              // platform logos), each scoped by the request transaction:
              io.openaev.rest.challenge.ChallengeApi.class,
              io.openaev.rest.channel.ChannelApi.class,
              io.openaev.rest.exercise.ExerciseApi.class,
              io.openaev.rest.asset.security_platforms.SecurityPlatformApi.class,
              // Services and exporters behind those endpoints, scoped by the request transaction:
              io.openaev.rest.exercise.service.ExportService.class,
              io.openaev.rest.inject.service.InjectService.class,
              io.openaev.rest.inject.service.InjectExportService.class,
              io.openaev.rest.inject.service.InjectDuplicateService.class,
              io.openaev.rest.payload.exports.PayloadFileExport.class,
              io.openaev.service.AtomicTestingService.class,
              io.openaev.service.scenario.ScenarioService.class,
              io.openaev.service.ScenarioToExerciseService.class,
              io.openaev.service.ZipJsonService.class,
              // Execution-engine attachment resolution, scoped by TenantScopedJobRunner which opens
              // the tenant transaction each inject execution runs under:
              io.openaev.executors.Injector.class,
              io.openaev.executors.InjectorContext.class,
              // Import path: resolves the write tenant explicitly and stamps rows before save:
              io.openaev.importer.V1_DataImporter.class,
              // Background telemetry: counts across all tenants explicitly (countAcrossAllTenants):
              io.openaev.telemetry.metric_collectors.ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(io.openaev.database.repository.DocumentRepository.class)
          .because(
              "documents is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule documents_challenge_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Force-initializes the challenge documents inside the read transaction
              // (ChallengeService.enrichChallengeWithExercisesOrScenarios) so open-in-view cannot
              // return an empty list:
              io.openaev.service.ChallengeService.class,
              // DTO mappers and exporters that map the documents to ids inside the request
              // transaction:
              io.openaev.rest.challenge.output.ChallengeOutput.class,
              io.openaev.rest.challenge.response.PublicChallenge.class,
              io.openaev.rest.document.DocumentService.class,
              io.openaev.service.scenario.ScenarioService.class,
              io.openaev.rest.exercise.exports.ExerciseFileExport.class,
              io.openaev.rest.inject.exports.InjectsFileExport.class)
          .should()
          .callMethod(io.openaev.database.model.Challenge.class, "getDocuments")
          .because(
              "documents is reached through Challenge's LAZY documents association without touching"
                  + " DocumentRepository. A getDocuments() outside a scoped transaction silently"
                  + " loads zero rows (open-in-view). New callers must run inside a tenant-scoped"
                  + " transaction and be allowlisted here");

  @ArchTest
  static final ArchRule documents_article_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Force-initializes the article documents inside the scoped read transaction
              // (ChannelService.withDocumentLinksInitialized) so the open-in-view serialization of
              // the channel reader cannot return an empty list:
              io.openaev.service.ChannelService.class,
              // Article write paths on TxCtx-carrying endpoints, resolving and rebinding the
              // documents inside the request transaction:
              io.openaev.rest.channel.ChannelApi.class,
              // Player document lists, walked inside the TxCtx-carrying player endpoints:
              io.openaev.rest.document.DocumentService.class,
              // DTO mapper materializing the ids inside the request transaction:
              io.openaev.rest.channel.output.ArticleOutput.class,
              // Duplication and scenario-to-simulation copies, inside their scoped transactions:
              io.openaev.rest.exercise.service.ExerciseService.class,
              io.openaev.service.scenario.ScenarioService.class,
              io.openaev.service.ScenarioToExerciseService.class,
              // Exporter mapping the article documents inside the export endpoint's transaction:
              io.openaev.rest.inject.exports.InjectsFileExport.class)
          .should()
          .callMethod(io.openaev.database.model.Article.class, "getDocuments")
          .because(
              "documents is reached through Article's LAZY documents association without touching"
                  + " DocumentRepository. A getDocuments() outside a scoped transaction silently"
                  + " loads zero rows (open-in-view). New callers must run inside a tenant-scoped"
                  + " transaction and be allowlisted here");

  @ArchTest
  static final ArchRule documents_exercise_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // The document removal and logo update handlers (TxCtx) keep the collection loaded
              // and consistent inside the request transaction, so the raw entity they return
              // serializes it; ExerciseService holds the initializer and the duplication copy:
              ExerciseApi.class,
              ExerciseService.class,
              // Force-initializes the parent documents of the channel reader inside the scoped
              // read transaction (ChannelService.withDocumentLinksInitialized):
              ChannelService.class,
              // Article write paths on TxCtx-carrying endpoints, linking the article documents to
              // the exercise inside the request transaction:
              ChannelApi.class,
              // Inject create and update paths, reached from the TxCtx-carrying inject handlers,
              // linking the inject documents to the exercise inside the request transaction:
              InjectService.class,
              InjectApi.class,
              // Chaining engine step data, built under the per-tenant transaction the queue and
              // step handlers open:
              InjectExecutionStep.class,
              // Exporter mapping the exercise documents inside the export endpoint's transaction:
              ExerciseFileExport.class)
          .should()
          .callMethod(Exercise.class, "getDocuments")
          .because(
              "documents is reached through Exercise's LAZY documents association without touching"
                  + " DocumentRepository. A getDocuments() outside a scoped transaction silently"
                  + " loads zero rows (open-in-view). New callers must run inside a tenant-scoped"
                  + " transaction and be allowlisted here");

  @ArchTest
  static final ArchRule documents_scenario_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Export, duplication and article copy, inside their scoped transactions:
              ScenarioService.class,
              // Scenario-to-simulation copy, reached from the TxCtx-carrying launch handlers, the
              // autonomous run's tenant transaction and the per-tenant scheduled scenario job:
              ScenarioToExerciseService.class,
              // Force-initializes the parent documents of the channel reader inside the scoped
              // read transaction (ChannelService.withDocumentLinksInitialized):
              ChannelService.class,
              // Article write paths on TxCtx-carrying endpoints, linking the article documents to
              // the scenario inside the request transaction:
              ChannelApi.class,
              // Inject create and update paths, reached from the TxCtx-carrying inject handlers,
              // linking the inject documents to the scenario inside the request transaction:
              InjectService.class,
              ScenarioInjectService.class,
              // Chaining engine step data, built under the per-tenant transaction the queue and
              // step handlers open:
              InjectExecutionStep.class)
          .should()
          .callMethod(Scenario.class, "getDocuments")
          .because(
              "documents is reached through Scenario's LAZY documents association without touching"
                  + " DocumentRepository. A getDocuments() outside a scoped transaction silently"
                  + " loads zero rows (open-in-view). New callers must run inside a tenant-scoped"
                  + " transaction and be allowlisted here");

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
  static final ArchRule phishing_results_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Owns the tracking lifecycle; scoped via TxCtx on the token-only public routes
              // (REQUIRES_NEW) and via the ambient TxCtx on the tenant-prefixed legacy route.
              PhishingTrackingService.class,
              // The one deliberately cross-tenant read, resolving a token's owning tenant with no
              // tenant of its own to scope with, under TxCtx.allTenants() through the background
              // primitive.
              PhishingTrackingPublicLookupService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(PhishingResultRepository.class)
          .because(
              "phishing_results is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

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
  static final ArchRule organizations_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Owning service: reads are scoped by the calling transaction (OrganizationApi's
              // TxCtx, the ManagerCreator scope for built-in injectors), writes carry an explicit
              // tenant id.
              OrganizationService.class,
              // TxCtx-carrying entrypoints linking a user/team to an organization through
              // updateRelation: the inspector scopes the findById, so a foreign organization is
              // not found.
              MeApi.class,
              PlayerApi.class,
              TeamApi.class,
              // Service behind PlayerApi#createPlayer / #upsertPlayer, same updateRelation lookup.
              PlayerService.class,
              // Organization search behind the TxCtx-carrying FullTextSearchApi handlers.
              FullTextSearchService.class,
              // HTTP-triggered importer; resolves the write tenant explicitly before reusing or
              // creating an organization.
              V1_DataImporter.class,
              // Background telemetry reader scoped via tenantTx.execute(TxCtx.allTenants()):
              ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(OrganizationRepository.class)
          .because(
              "organizations is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

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
              // Reads the injector type of an inject a user just created, updated or duplicated.
              // Every caller runs under its HTTP entrypoint's TxCtx-scoped transaction (inject,
              // atomic testing, simulation, scenario and autonomous run APIs), so the lazy load
              // resolves; a miss would only export "unknown":
              CredentialMetricCollector.class,
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
              ProductInventoryMetricCollector.class,
              // One pass after an upgrade over every tenant's findings, each batch in
              // TxCtx.allTenants() (FindingValueDisplayBackfillTest).
              FindingValueDisplayBackfill.class)
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

  @ArchTest
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
              AttackPatternUpsertService.class)
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
              AttackPatternUpsertService.class,
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
  static final ArchRule attack_patterns_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              AttackPatternApi.class,
              MitigationApi.class,
              // Services behind wired handlers; every lookup names the tenant it writes to, or
              // relies on the handler's scope:
              AttackPatternService.class,
              AttackPatternUpsertService.class,
              InjectorContractService.class,
              PayloadCreationService.class,
              PayloadUpdateService.class,
              PayloadUpsertService.class,
              PhishingLandingPageService.class,
              // Registration path: resolves the attack patterns of a contract within the
              // injector's own tenant, which it passes explicitly:
              InjectorService.class,
              // Import path: resolves the write tenant explicitly and stamps rows before save:
              V1_DataImporter.class,
              // Indexing: the sweep runs under TxCtx.allTenants(), and findForIndexing is
              // deliberately cross-tenant:
              AttackPatternHandler.class,
              // Attack-path widget: findAllById resolves the ids Elasticsearch returned, on the
              // request thread after both detached futures are joined, so it runs under the
              // scope of the @Transactional endpoint that called it. Proved through the real
              // endpoint by DashboardAttackPathIsolationTest:
              EsAttackPathService.class,
              // Background telemetry: counts across all tenants explicitly (countAcrossAllTenants):
              ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(AttackPatternRepository.class)
          .because(
              "attack_patterns is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule attack_patterns_association_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Hydrate the association inside the TxCtx-scoped transaction, before the
              // open-in-view JSON rendering (the #7026 shape applied to this table):
              AttackPatternInitializer.class,
              KillChainPhaseInitializer.class,
              // Derived *_attack_patterns getters on the aggregates themselves:
              Inject.class,
              InjectorContract.class,
              // Map or hydrate patterns inside the scoped transactions of wired handlers:
              AttackPathGraphService.class,
              CapabilityResolverService.class,
              DetectionRemediationApi.class,
              InjectHelper.class,
              InjectService.class,
              InjectorContractFullOutput.class,
              InjectorContractService.class,
              MapperService.class,
              PayloadMapper.class,
              PayloadService.class,
              ResultUtils.class,
              ScenarioService.class,
              SecurityCoverageService.class,
              ThreatArsenalMapper.class,
              V1_DataImporter.class,
              V20260725_Fix_starter_pack_payload_contracts.class,
              WorkflowExportInitializer.class)
          .should()
          .callMethod(InjectorContract.class, "getAttackPatterns")
          .because(
              "attack_patterns is reached through InjectorContract's LAZY @ManyToMany WITHOUT"
                  + " touching the repository. The tenant scope is transaction-local and"
                  + " open-in-view renders after the commit, so a lazy load at rendering time"
                  + " silently serializes an EMPTY pattern list. New callers must run inside a"
                  + " scoped transaction and be allowlisted here");

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

  @ArchTest
  static final ArchRule agents_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // The only writer. Every create stamps the tenant from the registering executor's
              // own tenant before the save (EndpointService/CalderaExecutorService
              // setNewAgentAttributes), so no write depends on the removed v1 listener; the native
              // delete and the asset touch run inside the deleting caller's scope.
              AgentService.class,
              // Inject-target search rooted on Agent, reached from TxCtx-carrying HTTP only.
              AgentTargetSearchAdaptor.class,
              // Counts active agents for the autonomous capability report, inside the TxCtx scope
              // of POST /api/autonomous-runs/capabilities/resolve.
              CapabilityResolverService.class,
              // Inject-execution callbacks: the batch mixes tenants and loads each group's agents
              // inside TxCtx.forTenant(the group's inject tenant).
              BatchingInjectStatusService.class,
              // Execution-trace writers reading the agent by id, on the TxCtx-carrying callback
              // and inject-execution paths.
              InjectStatusService.class,
              InjectExecutionService.class,
              ExecutableInjectService.class,
              // Quartz heartbeat monitor: reads and updates one tenant's stale agents inside
              // TenantScopedTransaction#forEachTenant.
              AgentInactivityMonitorJob.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(AgentRepository.class)
          .because(
              "agents is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows, and an unscoped DELETE purges nothing while reporting success. New"
                  + " accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule asset_agent_jobs_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              // getEndpointJobs (both), cleanupAssetAgentJob (both), upsertEndpoint/register.
              EndpointApi.class,
              // Register/getEndpointJobs write and read path behind EndpointApi; every write sets
              // tenant explicitly from the owning agent, so it does not depend on the ambient v1
              // listener either.
              EndpointService.class,
              // Reads a job's inject/agent to log a retrieval trace, inside the caller's scope
              // (EndpointApi#cleanupAssetAgentJob or the scheduled-execution job runner).
              InjectStatusService.class,
              // Deletes jobs on workflow end; reached only from already-scoped callers (see
              // AssetAgentJobDeletionScopeTest and the activation report for the three causes).
              WorkflowEndService.class,
              // OpenAEV executor: creates an upgrade-command job for a resolved agent, INSERT-only
              // and tenant-attributed explicitly from the agent (VALUES inserts are not blocked by
              // the inspector) - reached from the scoped scheduled-execution job runner.
              OpenAEVExecutorContextService.class,
              // Background reader behind the inject-execution job's overloaded-agent check,
              // reached inside TenantScopedJobRunner#runInTenant.
              ExecutorUtils.class,
              // Constructor plumbing only: hands the repository to OpenAEVExecutorContextService /
              // OpenAEVExecutorIntegration without calling it directly.
              OpenAEVExecutorIntegrationFactory.class,
              OpenAEVExecutorIntegration.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(AssetAgentJobRepository.class)
          .because(
              "asset_agent_jobs is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows, and an unscoped DELETE purges nothing while reporting success. New"
                  + " accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule custom_domains_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Admin CRUD is TxCtx-carrying HTTP (CustomDomainApi). The one deliberately
              // cross-tenant read (the public domain-check lookup) is isolated behind this bean,
              // which opens its own TxCtx.allTenants() scope through the background primitive
              // (kept off the HTTP path per NO_PRIMITIVE_ON_HTTP_PATH):
              CustomDomainService.class, CustomDomainPublicLookupService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(CustomDomainRepository.class)
          .because(
              "custom_domains is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule collector_types_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // CollectorService.register/ensureCollectorTypeExists: reached from CollectorApi
              // (TxCtx-carrying HTTP) and from the built-in collectors' registerForTenant, which
              // ManagerCreator.createManager scopes with setScopeOnCurrentTransaction before
              // calling. PayloadUpsertService.upsertPayload: TxCtx-carrying HTTP (PayloadApi),
              // read-only lookup of an existing collector type:
              CollectorService.class, PayloadUpsertService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(CollectorTypeRepository.class)
          .because(
              "collector_types is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule phishing_email_templates_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Admin CRUD is TxCtx-carrying HTTP (PhishingEmailTemplateApi). PhishingExecutor
              // reads it from InjectsExecutionJob's tenantScopedJobRunner.runInTenant and from the
              // chaining queue's per-inject TenantScopedTransaction; the two integration classes
              // only wire the repository through to PhishingExecutor's constructor, they never
              // query it themselves:
              PhishingEmailTemplateService.class,
              // PhishingLandingPageService reads the email-template chooser's rows when
              // building a landing page's contract, and seedDefaultsIfEmpty seeds the default
              // template directly through this repository:
              PhishingLandingPageService.class,
              PhishingExecutor.class,
              PhishingInjectorIntegration.class,
              PhishingInjectorIntegrationFactory.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(PhishingEmailTemplateRepository.class)
          .because(
              "phishing_email_templates is tenant-active: an accessor without a tenant scope"
                  + " silently reads zero rows. New accessors must carry a scope and be allowlisted"
                  + " here");

  @ArchTest
  static final ArchRule phishing_landing_pages_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Same shape as phishing_email_templates above. The unauthenticated HostedPublicApi
              // routes carry no {tenantId} path segment, so TxCtxArgumentResolver resolves their
              // ctx to TxCtx.missing() regardless of the recipient's tenant bound onto the legacy
              // TenantContext - a direct repository read from HostedPublicApi would fail-closed.
              // It never touches this repository itself: it resolves the landing page through
              // PhishingLandingPagePublicLookupService instead, scoped to the one tenant recovered
              // from the per-recipient token (same shape as CustomDomainPublicLookupService, kept
              // off the HTTP path per NO_PRIMITIVE_ON_HTTP_PATH):
              PhishingLandingPageService.class,
              PhishingExecutor.class,
              PhishingInjectorIntegration.class,
              PhishingInjectorIntegrationFactory.class,
              PhishingLandingPagePublicLookupService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(PhishingLandingPageRepository.class)
          .because(
              "phishing_landing_pages is tenant-active: an accessor without a tenant scope"
                  + " silently reads zero rows. New accessors must carry a scope and be allowlisted"
                  + " here");

  @ArchTest
  static final ArchRule attackpath_execution_collector_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // AttackPathApi is TxCtx-carrying HTTP; ingestion runs inside InjectExecutionStep's
              // queue transaction (TenantScopedTransaction on the inject's tenant) or
              // ExerciseService's TxCtx-carrying HTTP call, and stamps the tenant explicitly from
              // the inject (AttackPathExecutionIngestionService,
              // row.setTenant(inject.getTenant())):
              AttackPathSecurityPlatformResolver.class, AttackPathExecutionIngestionService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(AttackPathExecutionCollectorRepository.class)
          .because(
              "attackpath_execution_collector is tenant-active: an accessor without a tenant scope"
                  + " silently reads zero rows. New accessors must carry a scope and be allowlisted"
                  + " here");

  @ArchTest
  static final ArchRule attackpath_execution_remediation_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // AttackPathGraphService: TxCtx-carrying HTTP (AttackPathApi). Ingestion: same
              // scoping as attackpath_execution_collector above:
              AttackPathGraphService.class, AttackPathExecutionIngestionService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(AttackPathExecutionRemediationRepository.class)
          .because(
              "attackpath_execution_remediation is tenant-active: an accessor without a tenant"
                  + " scope silently reads zero rows. New accessors must carry a scope and be"
                  + " allowlisted here");

  @ArchTest
  static final ArchRule payloads_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest and
              // PayloadHttpIsolationTest:
              PayloadApi.class,
              PayloadApiExporter.class,
              // Services behind those handlers, and behind every other TxCtx-carrying payload
              // write path (threat arsenal, upsert-by-collector):
              PayloadCreationService.class,
              PayloadService.class,
              PayloadUpdateService.class,
              PayloadUpsertService.class,
              // Delete guard, run by Hibernate inside the payload, contract and threat arsenal
              // deletes, all TxCtx-carrying:
              PayloadDeleteGuard.class,
              // Hashes legacy payloads on import, inside the scoped import transaction and with an
              // explicit tenantId:
              io.openaev.service.payload.PayloadContentHashService.class,
              // Reads the payload behind an inject's detection remediations; scoped by
              // InjectApi#getPayloadDetectionRemediations, which carries TxCtx:
              InjectService.class,
              // Attack-path graph reads (icon metadata); every AttackPathApi entrypoint that
              // reaches it carries TxCtx and is @Transactional(readOnly = true):
              AttackPathGraphService.class,
              // v1 import: every call site threads the TxCtx of the scoped import transaction;
              // the duplicate-detection lookups additionally bind an explicit tenantId:
              V1_DataImporter.class,
              // One-shot repair migration, tenant-scoped by MigrationProcessor's per-tenant
              // transaction and reading with an explicit tenantId parameter regardless:
              V20260725_Fix_starter_pack_payload_contracts.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(PayloadRepository.class)
          .because(
              "payloads is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule vulnerabilities_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Service behind every TxCtx-carrying vulnerability write path, pinned by
              // VulnerabilityHttpIsolationTest:
              VulnerabilityService.class,
              // Provisioning datapack: seeds the tenant's default vulnerabilities/CWEs, stamping
              // the tenant explicitly on each entity before save:
              V20260330_Default_tenant_data.class,
              // Background telemetry reader scoped via tenantTx.execute(TxCtx.allTenants()):
              ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(VulnerabilityRepository.class)
          .because(
              "vulnerabilities is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule reporting_schedules_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoint (create/update/delete schedule), pinned by
              // TenantScopedEntrypointsTxCtxArchTest:
              ReportingApi.class,
              // Resolves schedules by id with a plain findById inside that scoped transaction,
              // and attributes a new schedule from its parent reporting's tenant:
              ReportingService.class,
              // Cross-tenant engine loader: reads every tenant's schedules under allTenants(),
              // and persists the double-fire marker under the schedule's own tenant through
              // TenantScopedJobRunner (called from ReportingScheduleService):
              ReportingScheduleLoader.class,
              // Scheduling engine: wraps every markLastRun/requestGeneration call in the
              // schedule's own tenant through TenantScopedJobRunner:
              ReportingScheduleService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(ReportingScheduleRepository.class)
          .because(
              "reporting_schedules is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule reportings_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              ReportingApi.class,
              // Resolves reportings by id with a plain findById inside that scoped transaction,
              // and attributes a new reporting from the request's write scope:
              ReportingService.class,
              // Platform-wide telemetry gauge, scoped with countAcrossAllTenants():
              ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(ReportingRepository.class)
          .because(
              "reportings is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule catalog_items_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Called by the catalogue API and sync, inside their scoped transactions:
              CatalogService.class, CatalogListService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(CatalogItemRepository.class)
          .because(
              "catalog_items is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule catalog_releases_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Same, for the releases received:
              CatalogService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(CatalogReleaseRepository.class)
          .because(
              "catalog_releases is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule reporting_generations_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              ReportingApi.class,
              // Resolves generations by id with a plain findById inside that scoped transaction,
              // and attributes a new generation from its parent reporting's tenant:
              ReportingService.class,
              // Polls a generation to a terminal status under TenantScopedJobRunner, the
              // schedule's own tenant:
              ReportingScheduleService.class,
              // Render lifecycle (markRunning/completeWithSuccess/completeWithError), each under
              // TenantScopedJobRunner with the job's own captured tenant:
              PlaywrightReportingRenderer.class,
              // Native IN-list lookup used to mark report outputs read-only on the generic
              // documents surface, reached only from the TxCtx-carrying document entrypoints:
              io.openaev.rest.document.DocumentApi.class,
              DocumentService.class,
              // Fallback renderer (Playwright/Chromium unavailable): @Transactional, joins the
              // caller's already-scoped transaction synchronously, never runs on a background
              // thread:
              io.openaev.rest.reporting.service.NoopReportingRenderer.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(ReportingGenerationRepository.class)
          .because(
              "reporting_generations is tenant-active: an accessor without a tenant scope"
                  + " silently reads zero rows. New accessors must carry a scope and be"
                  + " allowlisted here");

  @ArchTest
  static final ArchRule datapacks_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Sole accessor: provisioning lookups/writes reached from MigrationProcessor,
              // already inside its own scoped transaction (setScopeOnCurrentTransaction on
              // onboarding, execute() at startup, one per tenant):
              DataPackService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(DataPackRepository.class)
          .because(
              "datapacks is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule teams_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
              io.openaev.rest.team.TeamApi.class,
              io.openaev.rest.exercise.ExerciseApi.class,
              io.openaev.rest.scenario.ScenarioApi.class,
              io.openaev.rest.comcheck.ComcheckApi.class,
              io.openaev.rest.lessons.ExerciseLessonsApi.class,
              io.openaev.rest.lessons.ScenarioLessonsApi.class,
              // Owns the by-id read for every team path: reads inside the request scope and is
              // fail-closed on an empty one:
              io.openaev.service.TeamService.class,
              // Services behind the entrypoints above; their team reads run in the scoped
              // transaction the entrypoint opened, except ExerciseService's isolated team
              // enablement, which opens its own and is scoped by the TxCtx its caller passes:
              io.openaev.rest.exercise.service.ExerciseService.class,
              io.openaev.rest.finding.FindingService.class,
              io.openaev.rest.inject.service.InjectService.class,
              io.openaev.rest.inject.service.SimulationInjectService.class,
              io.openaev.rest.user.PlayerService.class,
              io.openaev.search.FullTextSearchService.class,
              io.openaev.service.AtomicTestingService.class,
              io.openaev.service.ExerciseTeamUserService.class,
              io.openaev.service.InjectSearchService.class,
              io.openaev.service.scenario.ScenarioService.class,
              io.openaev.service.ScenarioToExerciseService.class,
              io.openaev.service.targets.search.TeamTargetSearchAdaptor.class,
              io.openaev.utils.mapper.ExerciseMapper.class,
              io.openaev.injectors.phishing.service.PhishingTrackingService.class,
              // Import paths: every team create stamps the tenant resolved for the import, every
              // read is keyed on it:
              io.openaev.importer.V1_DataImporter.class,
              io.openaev.service.InjectImportService.class,
              // Creates the wrapper team in the run's own tenant, validated when the run was
              // created:
              io.openaev.service.autonomous.AutonomousRunService.class,
              // Chaining engine: reads the scope's teams inside the transaction the engine opened:
              io.openaev.service.chaining.ScopeService.class,
              io.openaev.service.chaining.ScopeSnapshotService.class,
              io.openaev.service.chaining.WorkflowService.class,
              // Export path, HTTP-only in practice, still keyed on the legacy ambient tenant:
              io.openaev.export.WorkflowExportInitializer.class,
              // Indexing fetch, under the sweep's allTenants() scope:
              io.openaev.engine.model.team.TeamHandler.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(TeamRepository.class)
          .because(
              "teams is tenant-active: an accessor without a tenant scope silently reads zero rows."
                  + " New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule autonomous_objective_templates_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Sole accessor: the objective-template gallery service, reached only from
              // AutonomousRunApi.objectiveTemplates, whose TxCtx carries @RequireTenantSelector so
              // the scope pins exactly one tenant for both the read and the lazy built-in seed.
              AutonomousObjectiveTemplateService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(AutonomousObjectiveTemplateRepository.class)
          .because(
              "autonomous_objective_templates is tenant-active: an accessor without a tenant scope"
                  + " silently reads zero rows, and the seed it drives would attribute its rows to"
                  + " the wrong tenant. New accessors must carry a scope and be allowlisted here");

  /**
   * {@code parameters} (entity {@code Setting}) is dual-scope, not strict: a platform row ({@code
   * tenant_id IS NULL}) is readable under any scope, the empty one included, so reads need no
   * review here. What a tenant must never do is WRITE it: only a platform-write transaction ({@code
   * io.openaev.aop.PlatformWrite}, {@code TenantScopedTransaction#executeAsPlatformWrite}/{@code
   * executeNewAsPlatformWrite}) may, and a refused write raises. So the guard follows the writers:
   * every method that modifies a {@code Setting} (see {@link PlatformRowWriters}), directly or
   * through {@code SettingService} and {@code PlatformSettingsService}, is reached only through
   * such a marker. The methods of {@link PlatformRowWriters#TENANT_ROW_WRITERS} write tenant rows
   * only, each with its reason. A tenant row read with no scope still comes back empty; the readers
   * of tenant rows are covered by {@link #parameters_tenant_row_readers_off_the_request_thread}.
   *
   * <p>Covered, as a call or as a method reference: the settings repository writes (saveAll
   * included), a setter of a {@code Setting} (dirty checking), an {@code EntityManager} / {@code
   * Session} write of a {@code Setting}, a query {@code executeUpdate} and a JDBC statement whose
   * text names the table, a {@code @Modifying} query of another repository naming it. Not covered:
   * Flyway migrations, a statement assembled at run time, a {@code Setting} handled as {@code
   * Object}, reflection, a write reached only through an interface call or an event dispatch, and
   * which lambda or method reference is the one handed to the primitive (its creator method
   * decides). At run time the database refuses a platform row written outside a platform-write
   * transaction: {@code require_tenant_write} for what goes through Hibernate and a row trigger on
   * {@code parameters} for raw JDBC (both pinned by {@code PlatformSettingsUnscopedAccessTest}).
   * The full list is in {@link PlatformRowWriters}.
   */
  @ArchTest
  static final ArchRule parameters_platform_row_writers_run_under_platform_write =
      methods()
          .that(PlatformRowWriters.writeASetting(PlatformRowWriters.TENANT_ROW_WRITERS))
          .should(PlatformRowWriters.beReachedOnlyUnderPlatformWrite())
          .because(
              "parameters is dual-scope: a write of a platform row outside a platform-write"
                  + " transaction is refused with an error. Every writer the rule can see (settings"
                  + " repository writes, Setting setters, EntityManager/Session writes, statements"
                  + " naming the table, as a call or a method reference) must sit under"
                  + " @PlatformWrite or executeAsPlatformWrite/executeNewAsPlatformWrite. Not seen"
                  + " here: migrations, run-time-built statements, a Setting handled as Object,"
                  + " reflection, a write reached only through an interface call or an event"
                  + " dispatch, and which lambda or method reference is the one handed to the"
                  + " primitive (the creator method is what counts). The database refusal covers"
                  + " what the rule misses only when it goes through Hibernate");

  /**
   * Each tenant-row-only exemption is still a method that modifies a {@code Setting}, and still
   * touches tenant rows only ({@link PlatformRowWriters#tenantRowOnlyViolations}): an entry for a
   * method that no longer writes, or that gained a path to a platform row, would stay behind as an
   * unexplained hole in the guard above.
   */
  @ArchTest
  static void parameters_tenant_row_writer_exemptions_are_live(JavaClasses classes) {
    for (PlatformRowWriters.TenantRowWriter exemption : PlatformRowWriters.TENANT_ROW_WRITERS) {
      java.util.List<com.tngtech.archunit.core.domain.JavaMethod> exempted =
          classes.stream()
              .filter(c -> c.isAssignableTo(exemption.owner()))
              .flatMap(c -> c.getMethods().stream())
              .filter(m -> PlatformRowWriters.isTenantRowWriter(m, java.util.List.of(exemption)))
              .toList();
      assertTrue(
          exempted.stream().anyMatch(PlatformRowWriters::writesASetting),
          () ->
              exemption.owner().getSimpleName()
                  + "#"
                  + exemption.method()
                  + exemption.parameterTypes().stream().map(Class::getSimpleName).toList()
                  + " is exempted as a tenant-row-only writer but no longer modifies a Setting:"
                  + " remove the entry");
      for (com.tngtech.archunit.core.domain.JavaMethod method : exempted) {
        java.util.List<String> violations = PlatformRowWriters.tenantRowOnlyViolations(method);
        assertTrue(
            violations.isEmpty(),
            () ->
                "an exempted tenant-row-only writer reaches more than tenant rows: "
                    + violations
                    + ". Mark its callers with the platform-write marker and remove the entry");
      }
    }
  }

  /**
   * A tenant row of {@code parameters} read with no tenant scope comes back empty, silently: the
   * platform rows are readable under any scope, the tenant rows are not. Every method that reads
   * one (any repository read not named for the platform row) must be reached on every path through
   * a tenant scope: a {@code @Transactional} method with a {@code TxCtx} parameter (HTTP and
   * request-bound services), a {@code TenantScopedTransaction} primitive around the work (jobs,
   * startup, consumers) or {@code setScopeOnCurrentTransaction} before the read. A reader reached
   * from a root that sets none is a defect, not a baseline entry.
   */
  @ArchTest
  static final ArchRule parameters_tenant_row_readers_off_the_request_thread =
      methods()
          .that(PlatformRowWriters.readATenantRowOfSettings())
          .should(PlatformRowWriters.beReachedOnlyUnderATenantScope())
          .because(
              "a tenant row of the dual-scope parameters table read with no scope comes back empty"
                  + " instead of failing");

  /**
   * {@code @PlatformWrite} is read by {@code TenantScopeTransactionAspect} only on a method
   * annotated {@code @Transactional} that takes a {@code TxCtx}; anywhere else it marks nothing
   * while looking like it does.
   */
  @ArchTest
  static final ArchRule platform_write_is_honoured_only_on_a_transactional_method_with_a_tx_ctx =
      methods()
          .that()
          .areAnnotatedWith(io.openaev.aop.PlatformWrite.class)
          .should(PlatformRowWriters.beHonouredByTheAspect())
          .because(
              "the aspect sets app.platform_write only for a @Transactional method with a TxCtx"
                  + " parameter");

  private static final Reaching SCENARIO_SERVICE_READS =
      reaching(
          io.openaev.service.scenario.ScenarioService.class, "getScenarioById", "getScenarioTeams");

  private static final Reaching V1_DATA_IMPORTER_READS =
      reaching(
          io.openaev.importer.V1_DataImporter.class,
          "buildStepTtpFromInjectorContract",
          "evaluateChainingStepResolvability",
          "findContractByPayloadExternalId",
          "handleInjectContent",
          "importInjectorContractFromStarterPack",
          "importInjects",
          "readableContractForPayload",
          "resolveStepData",
          "rewriteInjectInjector",
          "shouldResolveContractFromStepData");

  /**
   * The classes that reach {@code injectors_contracts} through a few methods only, each named here.
   * The scope those methods run under is checked in the bytecode, not described: {@link
   * #INJECTOR_CONTRACT_SCOPED_METHODS} fixes the methods, {@link #INJECTOR_CONTRACT_CALLER_CLAIMS}
   * lists and checks the callers of those that take no {@link TxCtx}, and {@code
   * injectors_contracts_scoped_methods_take_a_tx_ctx} checks the rest.
   */
  private static final Reaching[] INJECTOR_CONTRACT_SCOPED_METHODS = {
    // sendEmail reads the email contract of the tenant it is given, in the scope its caller
    // opened: the callers are listed in INJECTOR_CONTRACT_CALLER_CLAIMS.
    reaching(io.openaev.service.MailingService.class, "sendEmail"),
    // The scenario detail and the lessons teams take the TxCtx of their endpoint.
    SCENARIO_SERVICE_READS,
    // The import entry point takes the TxCtx, and every method below is private to the class and
    // reached from it.
    V1_DATA_IMPORTER_READS,
    // Callers listed in INJECTOR_CONTRACT_CALLER_CLAIMS.
    reaching(
        io.openaev.rest.document.DocumentService.class, "findAllDistinctOnInjectsByScenarioId"),
    reaching(io.openaev.rest.inject.service.InjectIndexCleanupService.class, "injectIdsByPayloadId")
  };

  /**
   * The classes allowlisted above and the classes of {@link #INJECTOR_CONTRACT_SCOPED_METHODS}. The
   * two rules that take them are class-level (a dependency, a call), so the scoped classes are left
   * out of them and checked by the method-level rules instead.
   */
  private static Class<?>[] withScopedClasses(Class<?>... listed) {
    return Stream.concat(
            Arrays.stream(listed),
            Arrays.stream(INJECTOR_CONTRACT_SCOPED_METHODS).map(Reaching::in))
        .toArray(Class<?>[]::new);
  }

  @ArchTest
  static final ArchRule payload_identities_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // No repository: an identity is only read and written through its payload.
              Payload.class, HasPayloadIdentity.class, InjectorContract.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(PayloadIdentity.class)
          .because(
              "payload_identities is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule workflows_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Chaining engine services. HTTP reach is through the TxCtx-carrying chaining and
              // simulation entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest; background
              // reach is through QueueChainingJob (allTenants() poll, forTenant per entry),
              // WorkflowTimeoutJob (allTenants() poll, forTenant per run), the step event consumers
              // (forTenant of the event) and InjectsFinalizationJob (per-tenant block):
              io.openaev.service.chaining.WorkflowService.class,
              io.openaev.service.chaining.WorkflowEndService.class,
              io.openaev.service.chaining.WorkflowResumeService.class,
              io.openaev.service.chaining.ConditionService.class,
              // Reads a run's own tenant_id by primary key to stamp its ready events; like its
              // other reads, it sees the run only when the caller's scope includes its tenant:
              io.openaev.service.chaining.QueueChainingService.class,
              // Attack-path seeding, reached only from the TxCtx-carrying AttackPathApi:
              io.openaev.service.attackpath.AttackPathCausalSeedService.class,
              // Background telemetry: counts across all tenants explicitly (countAcrossAllTenants):
              io.openaev.telemetry.metric_collectors.ProductInventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(WorkflowRepository.class)
          .because(
              "workflows is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule steps_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Chaining engine services, reached as described on the workflows rule:
              io.openaev.service.chaining.StepService.class,
              io.openaev.service.chaining.StepEventService.class,
              io.openaev.service.chaining.ConditionService.class,
              io.openaev.service.chaining.ChainingStepCleanupService.class,
              io.openaev.service.chaining.QueueChainingService.class,
              // Attack-path graph and seeding, reached only from the TxCtx-carrying AttackPathApi:
              io.openaev.service.attackpath.AttackPathGraphService.class,
              io.openaev.service.attackpath.AttackPathCausalSeedService.class,
              // Takes a reference by id to link a phishing result to its step, no read:
              io.openaev.injectors.phishing.service.PhishingTrackingService.class,
              // Existence check inside the TxCtx-carrying injector contract entrypoints:
              io.openaev.rest.injector_contract.InjectorContractService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(StepRepository.class)
          .because(
              "steps is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule conditions_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Chaining engine services, reached as described on the workflows rule:
              io.openaev.service.chaining.ConditionService.class,
              io.openaev.service.chaining.WorkflowStateService.class,
              // Attack-path graph and seeding, reached only from the TxCtx-carrying AttackPathApi:
              io.openaev.service.attackpath.AttackPathGraphService.class,
              io.openaev.service.attackpath.AttackPathCausalSeedService.class,
              // Export path, reached from the TxCtx-carrying simulation and scenario exports:
              io.openaev.export.WorkflowExportInitializer.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(ConditionRepository.class)
          .because(
              "conditions is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule scope_variables_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Sole accessor, keyed by workflow, inside the engine's scoped transaction:
              io.openaev.service.chaining.WorkflowService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(ScopeVariableRepository.class)
          .because(
              "scope_variables is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule steps_delay_queue_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Sole accessor. The due-entry poll runs under QueueChainingJob's allTenants()
              // scope, each popped entry under forTenant of its run:
              io.openaev.service.chaining.StepDelayQueueService.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(StepDelayQueueRepository.class)
          .because(
              "steps_delay_queue is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule workflow_scope_rules_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Chaining engine services, reached as described on the workflows rule:
              io.openaev.service.chaining.WorkflowService.class,
              io.openaev.service.chaining.ScopeService.class,
              // BEFORE_COMMIT listener, runs in the deleting request's scoped transaction:
              io.openaev.service.chaining.WorkflowScopeRuleCascadeListener.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(WorkflowScopeRuleRepository.class)
          .because(
              "workflow_scope_rules is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule workflow_states_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Sole accessor, keyed by run, inside the step event consumers' forTenant scope:
              io.openaev.service.chaining.WorkflowStateStore.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(WorkflowStateRepository.class)
          .because(
              "workflow_states is tenant-active: an accessor without a tenant scope silently reads"
                  + " zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule injectors_contracts_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              withScopedClasses(
                  // TxCtx-carrying entrypoints, pinned by TenantScopedEntrypointsTxCtxArchTest:
                  io.openaev.rest.attack_pattern.AttackPatternApi.class,
                  io.openaev.rest.exercise.ExerciseApi.class,
                  io.openaev.rest.inject.SimulationInjectApi.class,
                  io.openaev.rest.injector.InjectorApi.class,
                  // Services reached only from TxCtx-carrying entrypoints, reading in the scope the
                  // entrypoint opened (contract, threat arsenal, workflow, payload, atomic testing,
                  // mapper export, attack path graph, autonomous capability and grant APIs):
                  io.openaev.rest.injector_contract.InjectorContractService.class,
                  io.openaev.rest.payload.service.PayloadService.class,
                  io.openaev.rest.payload.service.PayloadUpsertService.class,
                  io.openaev.service.AtomicTestingService.class,
                  io.openaev.service.MapperService.class,
                  io.openaev.service.attackpath.AttackPathGraphService.class,
                  io.openaev.service.autonomous.CapabilityResolverService.class,
                  io.openaev.service.GrantService.class,
                  // Scenario inject generation and the STIX bundle import. The STIX path throws
                  // unless
                  // the scope pins one tenant
                  // (SecurityCoverageService#handleSecurityCoverageProcessing,
                  // before any contract is read), and its placeholder contract is read in the
                  // scenario's own tenant (SecurityCoverageInjectService#createdInjectsForScenario
                  // AndSecurityCoverage, findByContractIdAndTenant):
                  io.openaev.rest.inject.service.InjectAssistantService.class,
                  io.openaev.service.stix.SecurityCoverageInjectService.class,
                  // Exports, behind the scenario and simulation export endpoints:
                  io.openaev.export.WorkflowExportInitializer.class,
                  // Injector registration and teardown: the injector endpoints, or the builtin
                  // registration under ManagerCreator's setScopeOnCurrentTransaction(forTenant):
                  io.openaev.service.InjectorService.class,
                  io.openaev.injectors.phishing.service.PhishingLandingPageService.class,
                  // Runtime migration, under the migration processor's forTenant scope:
                  io.openaev.processor.core.V20260725_Fix_starter_pack_payload_contracts.class,
                  // Sets setScopeOnCurrentTransaction(forTenant(simulation tenant)) per comcheck
                  // before
                  // the contract read:
                  io.openaev.scheduler.jobs.ComchecksExecutionJob.class,
                  // Chaining: authoring runs under the step endpoints' TxCtx (StepApi#createStep
                  // and
                  // siblings all declare one), execution under the ready-step worker's forTenant
                  // scope
                  // (StepEventService). The contract reads of this class are the authoring lookups
                  // in
                  // the step's parent tenant and the findById of the allowlisted run path.
                  io.openaev.api.chaining.InjectExecutionStep.class))
          .should()
          .dependOnClassesThat()
          .areAssignableTo(io.openaev.database.repository.InjectorContractRepository.class)
          .orShould()
          .dependOnClassesThat()
          .areAssignableTo(io.openaev.database.helper.InjectorContractRepositoryHelper.class)
          .because(
              "injectors_contracts is tenant-active: an accessor without a tenant scope silently"
                  + " reads zero rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule injectors_contracts_native_query_access_is_reviewed =
      queryNamingTableIsCalledOnlyBy(
          "injectors_contracts",
          withScopedClasses(
              // The repository's own default methods delegate to its queries:
              io.openaev.database.repository.InjectorContractRepository.class,
              // Search indexing fetches, under the engine sync job's allTenants() scope:
              io.openaev.engine.model.inject.InjectHandler.class,
              io.openaev.engine.model.injectexpectation.InjectExpectationHandler.class,
              io.openaev.engine.model.scenario.ScenarioHandler.class,
              io.openaev.engine.model.simulation.SimulationHandler.class,
              // Reached only from TxCtx-carrying entrypoints (the contract, threat arsenal and
              // payload endpoints, the injector endpoints or the builtin registration scope,
              // scenario
              // inject generation, the STIX bundle import):
              io.openaev.rest.injector_contract.InjectorContractService.class,
              io.openaev.rest.payload.service.PayloadService.class,
              io.openaev.service.InjectorService.class,
              io.openaev.rest.inject.service.InjectAssistantService.class,
              io.openaev.service.stix.SecurityCoverageInjectService.class));

  @ArchTest
  static final ArchRule
      injectors_contracts_scoped_classes_reach_the_table_only_through_their_methods =
          tableIsReachedOnlyThrough(
              "injectors_contracts",
              access ->
                  access.getTargetOwner().isAssignableTo(InjectorContractRepository.class)
                      || access
                          .getTargetOwner()
                          .isAssignableTo(
                              io.openaev.database.helper.InjectorContractRepositoryHelper.class)
                      || isQueryNaming(access, "injectors_contracts"),
              INJECTOR_CONTRACT_SCOPED_METHODS);

  @ArchTest
  static final ArchRule injectors_contracts_scoped_methods_take_a_tx_ctx =
      methodsReachingTheTableTakeATxCtx(SCENARIO_SERVICE_READS, V1_DATA_IMPORTER_READS);

  @ArchTest
  static final ArchRule injector_contract_tags_native_query_access_is_reviewed =
      queryNamingTableIsCalledOnlyBy(
          "injector_contract_tags",
          // Contract sync behind the payload, threat arsenal and import endpoints:
          io.openaev.rest.payload.service.PayloadService.class);

  @ArchTest
  static final ArchRule injectors_contracts_attack_patterns_native_query_access_is_reviewed =
      queryNamingTableIsCalledOnlyBy(
          "injectors_contracts_attack_patterns",
          // The repository's own default methods delegate to its queries. The one statement built
          // in a string, InjectorContractRepositoryHelper's search, is covered by the
          // injectors_contracts repository rule through the helper:
          io.openaev.database.repository.InjectorContractRepository.class,
          // Search indexing fetches, under the engine sync job's allTenants() scope:
          io.openaev.engine.model.inject.InjectHandler.class,
          io.openaev.engine.model.injectexpectation.InjectExpectationHandler.class,
          io.openaev.engine.model.snapshotobservation.AttackObservationHandler.class,
          // Reached only from TxCtx-carrying entrypoints:
          io.openaev.service.scenario.ScenarioService.class,
          io.openaev.rest.injector_contract.InjectorContractService.class,
          io.openaev.rest.payload.service.PayloadService.class,
          io.openaev.service.stix.SecurityCoverageInjectService.class);

  @ArchTest
  static final ArchRule injectors_contracts_domains_native_query_access_is_reviewed =
      queryNamingTableIsCalledOnlyBy(
          "injectors_contracts_domains",
          // Search indexing fetch, under the engine sync job's allTenants() scope:
          io.openaev.engine.model.injectexpectation.InjectExpectationHandler.class,
          // Contract sync behind the payload, threat arsenal and import endpoints:
          io.openaev.rest.payload.service.PayloadService.class);

  @ArchTest
  static final ArchRule injectors_contracts_vulnerabilities_native_query_access_is_reviewed =
      queryNamingTableIsCalledOnlyBy(
          "injectors_contracts_vulnerabilities",
          // Scenario inject generation and the STIX bundle import, both TxCtx-carrying:
          io.openaev.rest.inject.service.InjectAssistantService.class,
          io.openaev.service.stix.SecurityCoverageInjectService.class);

  @ArchTest
  static final ArchRule injectors_injector_contracts_native_query_access_is_reviewed =
      queryNamingTableIsCalledOnlyBy(
          "injectors_injector_contracts",
          // Contract sync behind the payload endpoints; injector registration under the injector
          // endpoints' TxCtx or ManagerCreator's forTenant scope:
          io.openaev.rest.payload.service.PayloadService.class,
          io.openaev.service.InjectorService.class);

  /** The calling methods allowed to make a tenant-less injector contract access. */
  private static final AllowedCall[] INJECTOR_CONTRACT_ALLOWED_CALLS = {
    // The singular endpoints: requireSingleTenant refuses any scope that does not pin
    // exactly one tenant before the call reaches the lookup.
    allowPinned(
        InjectorContractApi.class,
        "injectorContract",
        "injectorContract",
        "requireSingleTenant pins the scope to one tenant first",
        Pin.requireSingleTenant(
            InjectorContractApi.class,
            "injectorContract",
            InjectorContractService.class,
            "injectorContract")),
    allowPinned(
        InjectorContractApi.class,
        "updateInjectorContract",
        "updateInjectorContract",
        "requireSingleTenant pins the scope to one tenant first",
        Pin.requireSingleTenant(
            InjectorContractApi.class,
            "updateInjectorContract",
            InjectorContractService.class,
            "updateInjectorContract")),
    allowPinned(
        InjectorContractApi.class,
        "updateInjectorContractMapping",
        "updateInjectorContractTTPDomainsAndTags",
        "requireSingleTenant pins the scope to one tenant first",
        Pin.requireSingleTenant(
            InjectorContractApi.class,
            "updateInjectorContractMapping",
            InjectorContractService.class,
            "updateInjectorContractTTPDomainsAndTags")),
    allowPinned(
        InjectorContractApi.class,
        "deleteInjectorContract",
        "deleteInjectorContract",
        "requireSingleTenant pins the scope to one tenant first",
        Pin.requireSingleTenant(
            InjectorContractApi.class,
            "deleteInjectorContract",
            InjectorContractService.class,
            "deleteInjectorContract")),
    // The same bare lookup redone inside the service. Each of these four service methods is
    // itself a tenant-less access of this rule, so its callers are exactly the entries of this
    // list that name it, and a new caller fails the rule until it is listed with its own reason.
    allow(
        InjectorContractService.class,
        "injectorContract",
        "findByIdOrExternalId",
        "its callers are the entries of this list that name injectorContract: the pinned"
            + " InjectorContractApi#injectorContract and the ThreatArsenalService and exporter"
            + " entries below"),
    allow(
        InjectorContractService.class,
        "updateInjectorContract",
        "findByIdOrExternalId",
        "its only listed caller is the pinned InjectorContractApi#updateInjectorContract"),
    allow(
        InjectorContractService.class,
        "updateInjectorContractTTPDomainsAndTags",
        "findByIdOrExternalId",
        "its only listed caller is the pinned InjectorContractApi#updateInjectorContractMapping"
            + " (ThreatArsenalService calls the overload that takes the contract entity)"),
    allow(
        InjectorContractService.class,
        "deleteInjectorContract",
        "findByIdOrExternalId",
        "its only listed caller is the pinned InjectorContractApi#deleteInjectorContract"),
    // The raw list answers one row per contract id: both queries group on the id, and the
    // attack patterns of the copies in the scope are merged
    // (InjectorContractRawAttackPatternJoinTest, InjectorContractApiRawListTenantScopeTest).
    allow(
        InjectorContractApi.class,
        "injectContracts",
        "getAllRawInjectContracts",
        "a list of the request scope's contracts, one row per contract id"),
    allow(
        InjectorContractService.class,
        "getAllRawInjectContracts",
        "getAllRawInjectorsContracts",
        "a list of the request scope's contracts, one row per contract id"),
    allow(
        InjectorContractService.class,
        "getAllRawInjectContracts",
        "getAllRawInjectorsContractsGranted",
        "a list of the request scope's granted contracts, one row per contract id"),
    allow(
        InjectorContractRepository.class,
        "getAllRawInjectorsContractsGranted",
        "getAllRawInjectorsContractsGranted",
        "the default method delegates to the two-argument query of the same interface"),
    // Threat arsenal: each of these service methods has one ThreatArsenalApi endpoint as its
    // only caller (injector_contract_callers_are_the_claimed_ones), and that endpoint pins one
    // tenant: the plain GET through requireSingleTenant (injector_contract_pins_are_called_
    // before_the_lookup), update and duplicate through writeScopeResolver.tenantForWrite
    // (same rule), delete and security-platforms by a route that exists only under the tenant
    // prefix (injector_contract_routes_are_tenant_prefixed_only), where the path pins the tenant.
    allowPinned(
        ThreatArsenalService.class,
        "findById",
        "injectorContract",
        "ThreatArsenalApi#threatArsenal, its only caller, calls requireSingleTenant first",
        Pin.requireSingleTenant(
            ThreatArsenalApi.class, "threatArsenal", ThreatArsenalService.class, "findById")),
    allowPinned(
        ThreatArsenalService.class,
        "update",
        "injectorContract",
        "ThreatArsenalApi#updateAction, its only caller, calls tenantForWrite first, which"
            + " refuses a scope of several tenants",
        Pin.tenantForWrite(
            ThreatArsenalApi.class, "updateAction", ThreatArsenalService.class, "update")),
    allowPinned(
        ThreatArsenalService.class,
        "duplicate",
        "injectorContract",
        "ThreatArsenalApi#duplicateAction, its only caller, calls tenantForWrite first, which"
            + " refuses a scope of several tenants",
        Pin.tenantForWrite(
            ThreatArsenalApi.class, "duplicateAction", ThreatArsenalService.class, "duplicate")),
    allowPinned(
        ThreatArsenalService.class,
        "delete",
        "injectorContract",
        "it calls requireSingleTenant first, and its only caller ThreatArsenalApi#deleteAction is"
            + " mapped under the tenant prefix only",
        Pin.requireSingleTenant(
            ThreatArsenalService.class,
            "delete",
            InjectorContractService.class,
            "injectorContract")),
    allow(
        ThreatArsenalService.class,
        "getSecurityPlatformsForActionRemediation",
        "injectorContract",
        "its only caller ThreatArsenalApi#securityPlatformsFromAction is mapped under the tenant"
            + " prefix only"),
    // Reached from delete and from the bulk delete chain bulkDelete -> bulkDeleteInTenant ->
    // resolveEligibleIds, each the only caller of the next. bulkDelete calls tenantForWrite
    // before it hands the chain to ambientTenantBridge.callInTenant.
    allowPinned(
        ThreatArsenalService.class,
        "isEligibleForDeletion",
        "injectorContract",
        "reached from delete (tenant-prefixed route only) and from the bulk delete chain that"
            + " bulkDelete enters after tenantForWrite refused a scope of several tenants",
        Pin.tenantForWrite(
            ThreatArsenalService.class, "bulkDelete", AmbientTenantBridge.class, "callInTenant")),
    allow(
        ThreatArsenalApiExporter.class,
        "export",
        "injectorContract",
        "the endpoint is mapped under the tenant prefix only, and nothing else calls it"),
    // Reads that return every contract of the request scope that a specification or no filter
    // selects: no single row is picked by an id that repeats across tenants.
    allow(
        io.openaev.rest.attack_pattern.AttackPatternApi.class,
        "injectorContracts",
        "findAll",
        "findAll(InjectorContractSpecification.fromAttackPattern(id)) returns every contract of"
            + " the scope linked to the attack pattern"),
    allow(
        io.openaev.service.MapperService.class,
        "getInjectorContractsToExport",
        "findAll",
        "findAll(specification) lists the contracts of the scope that the request selects, one"
            + " per contract id: InjectorContractSpecification#onePerContractId keeps the"
            + " best-ranked copy that matches the request, as the search does"),
    allow(
        io.openaev.service.autonomous.CapabilityResolverService.class,
        "buildIndex",
        "findAll",
        "findAll() returns every contract of the scope, and the index keeps one candidate per"
            + " contract id for each technique and output type: the best-ranked copy (CopyRanking)"),
    // The platforms of one simulation's injects, a distinct set: the query joins Inject to its
    // contract on the contract id and tenant_id (Inject#injectorContract, a JoinFormula on
    // tenant_id), so an inject reads the contract of its own tenant. Pinned with two tenants
    // holding the same contract id by InjectorContractIdJoinTenantTest.SimulationPlatforms.
    allow(
        io.openaev.rest.exercise.ExerciseApi.class,
        "exercise",
        "findDistinctPlatformsByExerciseId",
        "the query is keyed by the simulation and joins each inject to the contract of its own"
            + " tenant"),
    // Keyed by one injector entity: the contracts linked to it. The entity's key is (id, tenant_id)
    // and the link joins it on both columns, read from the mapping by
    // InjectorContractQueryShapeTest.InjectorKey.
    allow(
        io.openaev.rest.injector.InjectorApi.class,
        "injectorInjectTypes",
        "findByInjectorsContaining",
        "keyed by the injector entity that findByInjectorId resolves under the"
            + " @RequireTenantSelector TxCtx the endpoint declares"),
    allow(
        io.openaev.service.InjectorService.class,
        "deleteInjector",
        "findByInjectorsContaining",
        "keyed by the injector entity being deleted"),
    // Payload-keyed: the key is a Payload entity, whose primary key is the single column
    // payload_id (Payload#id), so it names one tenant's row; the lookup is not widened by the
    // scope. The single-column key and the derived lookup are read from the mapping by
    // InjectorContractQueryShapeTest.PayloadKey.
    allow(
        io.openaev.rest.payload.service.PayloadService.class,
        "duplicate",
        "findInjectorContractByPayload",
        "keyed by the payload entity"),
    allow(
        io.openaev.rest.payload.service.PayloadService.class,
        "synchroniseInjectorContractBasedOnPayload",
        "findInjectorContractByPayload",
        "keyed by the payload entity"),
    allow(
        io.openaev.rest.payload.service.PayloadService.class,
        "synchroniseInjectorContractBasedOnPayload",
        "addContractToPayloadsInjectors",
        "joins the injector and the contract on the same tenant"),
    allow(
        io.openaev.rest.payload.service.PayloadService.class,
        "findPayloadWithRelatedEntities",
        "findRelatedIdsByPayloadId",
        "keyed by the payload id"),
    allow(
        io.openaev.rest.payload.service.PayloadService.class,
        "delete",
        "findContractIdsByPayloadIdPerTenant",
        "keyed by the payload id, and returns each contract with its own tenant"),
    allow(
        InjectorContractService.class,
        "findContractIdsByPayloadIdPerTenant",
        "findContractTenantPairsByPayloadId",
        "keyed by the payload id, and returns each contract with its own tenant"),
    allow(
        io.openaev.rest.payload.service.PayloadUpsertService.class,
        "updatePayloadFromUpsert",
        "findInjectorContractByPayload",
        "keyed by the payload entity"),
    allow(
        io.openaev.service.stix.SecurityCoverageInjectService.class,
        "createInjectsByInjectorContracts",
        "findInjectorContractByPayload",
        "keyed by the payload entity"),
    allow(
        io.openaev.importer.V1_DataImporter.class,
        "findContractByPayloadExternalId",
        "findInjectorContractByPayload",
        "keyed by a payload found in the write tenant by findByExternalIdAndTenantId"),
    allow(
        io.openaev.importer.V1_DataImporter.class,
        "readableContractForPayload",
        "findInjectorContractByPayload",
        "keyed by a Payload entity, whose primary key is the single column payload_id"),
    // Called only from InjectExecutionStep#run, which only StepEventService#run calls, which only
    // StepEventService#handleReadyStepEvent calls, inside its tenantTx.execute(TxCtx.forTenant(
    // tenantId)) transaction (injector_contract_callers_are_the_claimed_ones).
    allow(
        InjectExecutionStep.class,
        "getInjectFromDataStep",
        "findById",
        "reached only through the ready-step handler, which scopes its transaction to the step's"
            + " tenant with TxCtx.forTenant"),
    // A runtime migration is processed only by MigrationProcessor#init, which scopes each call
    // with TxCtx.forTenant (setScopeOnCurrentTransaction on onboarding, tenantTx.execute at
    // startup): injector_contract_callers_are_the_claimed_ones.
    allow(
        io.openaev.processor.core.V20260725_Fix_starter_pack_payload_contracts.class,
        "doMigrate",
        "findById",
        "reached only through MigrationProcessor#init, which scopes each tenant with"
            + " TxCtx.forTenant")
  };

  /**
   * The callers the allowlist reasons above name, checked in the bytecode of every analysed class.
   * Each claim lists every method that may call the target, so a new caller fails here instead of
   * going unseen behind a reason that was true when it was written.
   */
  private static final CallersClaim[] INJECTOR_CONTRACT_CALLER_CLAIMS = {
    claim(ThreatArsenalService.class, "findById", at(ThreatArsenalApi.class, "threatArsenal")),
    claim(ThreatArsenalService.class, "update", at(ThreatArsenalApi.class, "updateAction")),
    claim(ThreatArsenalService.class, "duplicate", at(ThreatArsenalApi.class, "duplicateAction")),
    claim(ThreatArsenalService.class, "delete", at(ThreatArsenalApi.class, "deleteAction")),
    claim(
        ThreatArsenalService.class,
        "getSecurityPlatformsForActionRemediation",
        at(ThreatArsenalApi.class, "securityPlatformsFromAction")),
    // Matched by name: the String overload is called by resolveEligibleIds, the contract overload
    // by delete and by the String overload.
    claim(
        ThreatArsenalService.class,
        "isEligibleForDeletion",
        at(ThreatArsenalService.class, "delete"),
        at(ThreatArsenalService.class, "resolveEligibleIds"),
        at(ThreatArsenalService.class, "isEligibleForDeletion")),
    claim(
        ThreatArsenalService.class,
        "resolveEligibleIds",
        at(ThreatArsenalService.class, "bulkDeleteInTenant")),
    claim(
        ThreatArsenalService.class,
        "bulkDeleteInTenant",
        at(ThreatArsenalService.class, "bulkDelete")),
    // The exporter endpoint is called by nothing: Spring serves it.
    claim(ThreatArsenalApiExporter.class, "export"),
    claim(InjectExecutionStep.class, "getInjectFromDataStep", at(InjectExecutionStep.class, "run")),
    claim(ActionStep.class, "run", at(StepEventService.class, "run")),
    claim(StepEventService.class, "run", at(StepEventService.class, "handleReadyStepEvent")),
    claim(Processable.class, "process", at(MigrationProcessor.class, "init")),
    // sendEmail is called by its own overloads, by the lessons endpoint (TxCtx), by the
    // notification dispatch (below) and by the services that open a forTenant scope around it.
    claim(
        io.openaev.service.MailingService.class,
        "sendEmail",
        at(io.openaev.service.MailingService.class, "sendEmail"),
        at(
            io.openaev.rest.lessons.ExerciseLessonsApi.class,
            "sendExerciseLessons",
            Scope.TX_CTX_PARAMETER),
        at(
            io.openaev.notification.engine.NotificationDispatchService.class,
            "handleEmailNotification"),
        at(
            io.openaev.service.UserService.class,
            "sendPasswordResetEmail",
            Scope.OPENED_AROUND_LAMBDA),
        at(io.openaev.service.UserService.class, "requestEmailChange", Scope.OPENED_AROUND_LAMBDA),
        at(
            io.openaev.service.UserService.class,
            "sendEmailChangeConfirmationEmail",
            Scope.OPENED_AROUND_LAMBDA),
        at(
            io.openaev.xtmhub.XtmHubEmailService.class,
            "sendLostConnectivityEmail",
            Scope.OPENED_AROUND_LAMBDA),
        at(
            io.openaev.xtmhub.XtmHubEmailService.class,
            "sendTenantLostConnectivityEmail",
            Scope.OPENED_AROUND_LAMBDA)),
    claim(
        io.openaev.notification.engine.NotificationDispatchService.class,
        "handleEmailNotification",
        at(io.openaev.notification.engine.NotificationDispatchService.class, "dispatch")),
    claim(
        io.openaev.notification.engine.NotificationDispatchService.class,
        "dispatch",
        at(
            io.openaev.notification.engine.NotificationDigestService.class,
            "processDigest",
            Scope.OPENED_AROUND_LAMBDA),
        at(
            io.openaev.notification.engine.NotificationEngineService.class,
            "handleEventWithMessage",
            Scope.OPENED_AROUND_LAMBDA),
        at(io.openaev.service.notification.NotifierService.class, "test", Scope.TX_CTX_PARAMETER)),
    // The scenario of the STIX bundle import: the method that reads the documents takes the
    // TxCtx the import runs under.
    claim(
        io.openaev.rest.document.DocumentService.class,
        "findAllDistinctOnInjectsByScenarioId",
        at(
            io.openaev.service.stix.SecurityCoverageInjectService.class,
            "createInjectsByArtifacts",
            Scope.TX_CTX_PARAMETER)),
    // The payload delete is the only caller, and its endpoint takes a TxCtx.
    claim(
        io.openaev.rest.inject.service.InjectIndexCleanupService.class,
        "injectIdsByPayloadId",
        at(io.openaev.rest.payload.service.PayloadService.class, "delete")),
    claim(
        io.openaev.rest.payload.service.PayloadService.class,
        "delete",
        at(io.openaev.rest.payload.PayloadApi.class, "deletePayload", Scope.TX_CTX_PARAMETER))
  };

  private static CallersClaim claim(Class<?> owner, String method, At... callers) {
    return new CallersClaim(owner, method, List.of(callers));
  }

  private static At at(Class<?> in, String method) {
    return new At(in, method);
  }

  private static At at(Class<?> in, String method, Scope scope) {
    return new At(in, method, scope);
  }

  private static Reaching reaching(Class<?> in, String... methods) {
    return new Reaching(in, List.of(methods));
  }

  @ArchTest
  static final ArchRule injector_contract_callers_are_the_claimed_ones =
      callersAreOnly(INJECTOR_CONTRACT_CALLER_CLAIMS);

  /** The endpoints whose allowlist reason says the route exists under the tenant prefix only. */
  @ArchTest
  static final ArchRule injector_contract_routes_are_tenant_prefixed_only =
      mappedOnTheTenantRouteOnly(
          new RouteClaim(ThreatArsenalApi.class, "deleteAction"),
          new RouteClaim(ThreatArsenalApi.class, "securityPlatformsFromAction"),
          new RouteClaim(ThreatArsenalApiExporter.class, "export"));

  /**
   * A built-in injector contract id/external id repeats across every tenant that holds a copy
   * (#8485), so a query on {@code injectors_contracts} with no tenant argument answers for every
   * tenant of the caller's scope: a lookup by id resolves a unique row only when that scope is
   * already pinned to one tenant, and a list or a technique read merges the copies. This rule keeps
   * such a query out of every class that is not allowlisted, whatever its name: {@link
   * #tenantlessInjectorContractQueryIsCalledOnlyBy} derives the set from {@link
   * InjectorContractRepository} and {@link InjectorContractService}, so a query added later is
   * covered without editing this file.
   *
   * <p>Out of scope by construction, not by oversight: the {@code CrudRepository} methods that take
   * the composite key or the entity ({@code findById(InjectorContractId)}, {@code findAllById},
   * {@code save}, {@code delete}), whose argument already carries {@code tenant_id}. {@code
   * TenantActiveTableAccessRulesTest} fires the rule from an unlisted class on a declared lookup, a
   * payload-keyed query, a read by specification, an argument-less {@code CrudRepository} read, a
   * method reference and a service wrapper, and checks that the tenant-carrying forms pass.
   */
  @ArchTest
  static final ArchRule injector_contract_tenantless_query_is_called_only_by =
      tenantlessInjectorContractQueryIsCalledOnlyBy(INJECTOR_CONTRACT_ALLOWED_CALLS);

  /**
   * The allowlist reasons that say a pin refuses a scope of several tenants first are checked here,
   * not taken on trust: the pin must be called in the endpoint, and before the call that reaches
   * the lookup. {@code TenantActiveTableAccessRulesTest} fires it on a pin that comes after the
   * lookup, on a missing pin, and on a pin in another method.
   */
  @ArchTest
  static final ArchRule injector_contract_pins_are_called_before_the_lookup =
      pinnedCallersPinBeforeTheLookup(INJECTOR_CONTRACT_ALLOWED_CALLS);

  /**
   * The five link tables of injectors_contracts have no repository of their own: an entity load of
   * the contract fetches them eagerly in the same scope, so the rule on the contract repository
   * covers that path. Their other readers are queries that name the table in SQL, mostly in the
   * repositories of other aggregates. This rule finds those queries by reflection rather than by a
   * list, so a new query naming the table is caught without editing this file, and allowlists the
   * classes that call one.
   *
   * <p>Blind spots, stated so nobody reads more into it: SQL assembled in a string outside a
   * {@code @Query} (each such class is named in the comment of the rule that needs it), and JPQL or
   * Criteria that reach a link table through the association name rather than the table name.
   */
  private static ArchRule queryNamingTableIsCalledOnlyBy(String table, Class<?>... allowed) {
    Pattern named = Pattern.compile("\\b" + table + "\\b", Pattern.CASE_INSENSITIVE);
    DescribedPredicate<JavaMethodCall> callsQueryNamingTable =
        DescribedPredicate.describe(
            "a repository @Query naming " + table,
            call ->
                call.getTarget()
                    .resolveMember()
                    .flatMap(method -> method.tryGetAnnotationOfType(Query.class))
                    .map(query -> named.matcher(query.value()).find())
                    .orElse(false));
    return noClasses()
        .that()
        .doNotBelongToAnyOf(allowed)
        .should()
        .callMethodWhere(callsQueryNamingTable)
        .because(
            table
                + " is tenant-active: a query naming it without a tenant scope silently reads zero"
                + " rows. New callers must carry a scope and be allowlisted here");
  }

  /** A repository {@code @Query} method that names {@code table}, called or referenced. */
  static boolean isQueryNaming(JavaAccess<?> access, String table) {
    if (!(access.getTarget() instanceof AccessTarget.CodeUnitAccessTarget codeUnit)) {
      return false;
    }
    Pattern named = Pattern.compile("\\b" + table + "\\b", Pattern.CASE_INSENSITIVE);
    return codeUnit
        .resolveMember()
        .flatMap(method -> method.tryGetAnnotationOfType(Query.class))
        .map(query -> named.matcher(query.value()).find())
        .orElse(false);
  }

  /** The methods of one class that may reach a table, the class not being allowlisted whole. */
  record Reaching(Class<?> in, List<String> methods) {}

  /**
   * Narrows a class allowlist to methods: in each class of {@code allowed}, an access that reaches
   * the table must be made by one of the class's listed methods (a lambda counts as its method),
   * and each listed method must make one, so the list stays exact. A class that was not analysed is
   * a violation, so a rename cannot turn the rule into a no-op.
   */
  static ArchRule tableIsReachedOnlyThrough(
      String table, Predicate<JavaAccess<?>> reachesTable, Reaching... allowed) {
    ArchCondition<JavaClass> onlyListedMethods =
        new ArchCondition<>("reach " + table + " only through the methods its allowlist names") {
          private final Set<String> observed = new HashSet<>();
          private final Set<String> analysed = new HashSet<>();

          @Override
          public void check(JavaClass javaClass, ConditionEvents events) {
            Optional<Reaching> entry =
                Arrays.stream(allowed)
                    .filter(reaching -> reaching.in().getName().equals(javaClass.getName()))
                    .findFirst();
            if (entry.isEmpty()) {
              return;
            }
            analysed.add(javaClass.getName());
            for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
              if (!reachesTable.test(access)) {
                continue;
              }
              String method = AllowedCall.enclosingMethodName(access.getOrigin());
              if (entry.get().methods().contains(method)) {
                observed.add(javaClass.getName() + "#" + method);
              } else {
                events.add(
                    SimpleConditionEvent.violated(
                        access,
                        access.getDescription() + " is not made by a method listed for the class"));
              }
            }
          }

          @Override
          public void finish(ConditionEvents events) {
            for (Reaching reaching : allowed) {
              String name = reaching.in().getSimpleName();
              if (!analysed.contains(reaching.in().getName())) {
                events.add(
                    new SimpleConditionEvent(
                        reaching, false, name + " was not analysed: its methods are not checked"));
                continue;
              }
              for (String method : reaching.methods()) {
                if (!observed.contains(reaching.in().getName() + "#" + method)) {
                  events.add(
                      new SimpleConditionEvent(
                          reaching,
                          false,
                          name + "#" + method + " is listed but does not reach " + table));
                }
              }
            }
          }
        };
    return classes()
        .should(onlyListedMethods)
        .because(
            "a class allowlisted for its methods reaches "
                + table
                + " only through them: a new method that reaches it is not covered by the scope the"
                + " listed ones run under");
  }

  /**
   * The methods of a class that reach a table, and the methods of the class that lead to them, take
   * a {@link TxCtx} whenever they are not private: the class is then reached only through an entry
   * point that carries the scope, since a private method has no caller outside the class. The
   * methods that lead to a listed method are found through the calls made inside the class, a
   * lambda counting as its method. A class with no such entry point is a violation, so the claim
   * cannot be true of nothing.
   */
  static ArchRule methodsReachingTheTableTakeATxCtx(Reaching... claims) {
    ArchCondition<JavaClass> entryPointsTakeATxCtx =
        new ArchCondition<>("take a TxCtx in every non-private method that reaches the table") {
          @Override
          public void check(JavaClass javaClass, ConditionEvents events) {
            Optional<Reaching> claim =
                Arrays.stream(claims)
                    .filter(reaching -> reaching.in().getName().equals(javaClass.getName()))
                    .findFirst();
            if (claim.isEmpty()) {
              return;
            }
            Set<String> leadsToTable = new HashSet<>(claim.get().methods());
            boolean grown = true;
            while (grown) {
              grown = false;
              for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
                if (access.getTargetOwner().getName().equals(javaClass.getName())
                    && leadsToTable.contains(access.getTarget().getName())
                    && leadsToTable.add(AllowedCall.enclosingMethodName(access.getOrigin()))) {
                  grown = true;
                }
              }
            }
            List<JavaMethod> entryPoints =
                javaClass.getMethods().stream()
                    .filter(method -> leadsToTable.contains(method.getName()))
                    .filter(method -> !method.getModifiers().contains(JavaModifier.PRIVATE))
                    .toList();
            if (entryPoints.isEmpty()) {
              events.add(
                  SimpleConditionEvent.violated(
                      javaClass,
                      javaClass.getSimpleName()
                          + " has no non-private method that reaches the table"));
            }
            for (JavaMethod method : entryPoints) {
              if (method.getRawParameterTypes().stream()
                  .noneMatch(type -> type.isEquivalentTo(TxCtx.class))) {
                events.add(
                    SimpleConditionEvent.violated(
                        method, method.getFullName() + " reaches the table and takes no TxCtx"));
              }
            }
          }

          @Override
          public void finish(ConditionEvents events) {}
        };
    return classes()
        .should(entryPointsTakeATxCtx)
        .because(
            "a class that reaches a tenant-active table through private methods is scoped by the"
                + " TxCtx of the non-private methods that lead to them");
  }

  /**
   * Finds every access to an {@link InjectorContractRepository} query, or to an {@link
   * InjectorContractService} method wrapping one, that takes no tenant argument. The set is derived
   * from the two classes, never from a list of names, so a method added later is covered without
   * editing this file.
   *
   * <p>A parameter carries a tenant when its type is {@code InjectorContract}, {@code
   * InjectorContractId}, {@code Tenant} or {@code TxCtx}, or when its name (the Spring Data
   * {@code @Param} value, or the compiled parameter name) contains "tenant". On the repository that
   * covers what it declares and what it inherits from {@code JpaSpecificationExecutor}
   * (specification reads, which carry no tenant); of {@code CrudRepository}'s methods only those
   * without a parameter ({@code findAll()}, {@code count()}, {@code deleteAll()}) qualify, since
   * the others take the composite key or the entity, which already carry {@code tenant_id}. On the
   * service it covers the public methods that reach such a query, directly or through a private
   * helper of the class.
   *
   * <p>The access is matched on the type the call is made against, which is {@code
   * InjectorContractRepository} even for an inherited method, and method references count as
   * accesses, so a {@code repository::findById} is seen as well as a call.
   */
  static ArchRule tenantlessInjectorContractQueryIsCalledOnlyBy(AllowedCall... allowed) {
    Set<Method> tenantlessServiceMethods = tenantlessInjectorContractServiceMethods();
    ArchCondition<JavaClass> noUnlistedTenantlessAccess =
        new ArchCondition<>(
            "not access an injector contract query that takes no tenant argument, unless the"
                + " calling method is allowlisted") {
          @Override
          public void check(JavaClass javaClass, ConditionEvents events) {
            for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
              if (isTenantlessQuery(access, tenantlessServiceMethods)
                  && Arrays.stream(allowed).noneMatch(call -> call.permits(access))) {
                events.add(SimpleConditionEvent.violated(access, access.getDescription()));
              }
            }
          }
        };
    return classes()
        .should(noUnlistedTenantlessAccess)
        .because(
            "a built-in injector contract id/external id repeats across tenants (#8485): a query"
                + " with no tenant argument resolves a unique row, or the right rows, only when the"
                + " caller's scope is already pinned to one tenant, and otherwise answers for every"
                + " tenant in the scope. New callers must pass the tenant (injectorContractInTenant"
                + " / findByContractIdAndTenant / findByIdOrExternalIdAndTenant) and be allowlisted"
                + " here, one calling method at a time, only with the reason that method's scope is"
                + " one tenant");
  }

  /**
   * One calling method allowed to make one tenant-less access, with the reason its scope is one
   * tenant. A lambda counts as the method that contains it. A reason that says a pin refuses a
   * scope of several tenants first carries the {@link Pin} it names, which {@link
   * #pinnedCallersPinBeforeTheLookup} checks.
   */
  record AllowedCall(Class<?> caller, String method, String target, String reason, Pin pin) {

    AllowedCall(Class<?> caller, String method, String target, String reason) {
      this(caller, method, target, reason, null);
    }

    boolean permits(JavaAccess<?> access) {
      JavaCodeUnit origin = access.getOrigin();
      return origin.getOwner().getName().equals(caller.getName())
          && enclosingMethodName(origin).equals(method)
          && access.getTarget().getName().equals(target);
    }

    static String enclosingMethodName(JavaCodeUnit origin) {
      Matcher lambda = LAMBDA_NAME.matcher(origin.getName());
      return lambda.matches() ? lambda.group(1) : origin.getName();
    }
  }

  private static final Pattern LAMBDA_NAME = Pattern.compile("lambda\\$(.+?)\\$\\d+");

  /**
   * A pin an allowlist reason relies on: {@code inClass#inMethod} calls {@code pinOwner#pinName},
   * which refuses a scope of several tenants, before the first call of {@code
   * lookupOwner#lookupName}, the call that reaches the contract lookup. For an endpoint the lookup
   * call is the one into its service; the allowlisted caller is then that service method. The pin
   * must run on every execution: no jump, switch, try block or catch handler comes before it.
   */
  record Pin(
      Class<?> inClass,
      String inMethod,
      Class<?> pinOwner,
      String pinName,
      Class<?> lookupOwner,
      String lookupName) {

    static Pin requireSingleTenant(
        Class<?> inClass, String inMethod, Class<?> lookupOwner, String lookupName) {
      return new Pin(
          inClass, inMethod, TxCtxScopeUtils.class, "requireSingleTenant", lookupOwner, lookupName);
    }

    static Pin tenantForWrite(
        Class<?> inClass, String inMethod, Class<?> lookupOwner, String lookupName) {
      return new Pin(
          inClass,
          inMethod,
          TenantWriteScopeResolver.class,
          "tenantForWrite",
          lookupOwner,
          lookupName);
    }

    /**
     * Why the pin does not always run first, or empty when it does. The pin must be a call of the
     * method itself, with no jump, switch, try block or catch handler before it, so that every
     * execution that reaches the lookup has run it; a pin inside an {@code if}, a {@code try} or a
     * lambda does not count.
     */
    Optional<String> violation() {
      List<MethodCallOrder.Call> calls = MethodCallOrder.callsInOrder(inClass, inMethod);
      Optional<Integer> lookup =
          MethodCallOrder.firstIndexOf(calls, lookupOwner.getName(), lookupName);
      Optional<Integer> anyPin = MethodCallOrder.firstIndexOf(calls, pinOwner.getName(), pinName);
      Optional<Integer> pinned =
          MethodCallOrder.firstUnconditionalIndexOf(calls, pinOwner.getName(), pinName);
      String where = inClass.getSimpleName() + "#" + inMethod;
      String pinCall = pinOwner.getSimpleName() + "#" + pinName;
      String lookupCall = lookupOwner.getSimpleName() + "#" + lookupName;
      if (lookup.isEmpty()) {
        return Optional.of(where + " never calls " + lookupCall);
      }
      if (anyPin.isEmpty()) {
        return Optional.of(where + " never calls " + pinCall + " before " + lookupCall);
      }
      if (pinned.isEmpty()) {
        return Optional.of(
            where
                + " calls "
                + pinCall
                + " only after a branch or inside a try block, so it does not always run before "
                + lookupCall);
      }
      return pinned.get() < lookup.get()
          ? Optional.empty()
          : Optional.of(where + " calls " + pinCall + " after " + lookupCall);
    }
  }

  /** Allowed access whose reason names a pin that must be called before the lookup. */
  private static AllowedCall allowPinned(
      Class<?> caller, String method, String target, String reason, Pin pin) {
    return new AllowedCall(caller, method, target, reason, pin);
  }

  /**
   * Every allowlisted call that names a {@link Pin} is checked in the bytecode of the method that
   * holds the pin: the pin is called, and before the call that reaches the lookup. A class that
   * holds a pin and is missing from the analysed classes is a violation too, so a renamed endpoint
   * cannot turn the check into a no-op.
   */
  static ArchRule pinnedCallersPinBeforeTheLookup(AllowedCall... allowed) {
    ArchCondition<JavaClass> pinsComeFirst =
        new ArchCondition<>("call the pin their allowlist reason names before the lookup") {
          private final Set<Pin> seen = new HashSet<>();

          @Override
          public void check(JavaClass javaClass, ConditionEvents events) {
            for (AllowedCall call : allowed) {
              Pin pin = call.pin();
              if (pin != null && pin.inClass().getName().equals(javaClass.getName())) {
                seen.add(pin);
                pin.violation()
                    .ifPresent(
                        message -> events.add(SimpleConditionEvent.violated(javaClass, message)));
              }
            }
          }

          @Override
          public void finish(ConditionEvents events) {
            for (AllowedCall call : allowed) {
              if (call.pin() != null && !seen.contains(call.pin())) {
                events.add(
                    new SimpleConditionEvent(
                        call,
                        false,
                        call.pin().inClass().getSimpleName()
                            + " was not analysed: its pin is not checked"));
              }
            }
          }
        };
    return classes()
        .should(pinsComeFirst)
        .because(
            "an allowlist reason that says a pin refuses a scope of several tenants first must be"
                + " true of the bytecode: without the pin, a built-in id repeated across the"
                + " tenants of the scope makes the lookup match several rows");
  }

  /**
   * A method, as the enclosing method of a call: a lambda counts as the method that holds it. A
   * caller that carries a {@link Scope} is also checked to run under it.
   */
  record At(Class<?> in, String method, Scope scope) {

    At(Class<?> in, String method) {
      this(in, method, null);
    }

    boolean isOrigin(JavaCodeUnit origin) {
      return origin.getOwner().getName().equals(in.getName())
          && AllowedCall.enclosingMethodName(origin).equals(method);
    }

    String describe() {
      return in.getSimpleName() + "#" + method;
    }
  }

  /**
   * The tenant scope a claimed caller runs the call under, read from the bytecode of the caller
   * class.
   */
  enum Scope {
    /** Every method of that name takes a {@link TxCtx}, which the transaction aspect scopes on. */
    TX_CTX_PARAMETER("takes a TxCtx parameter") {
      @Override
      boolean holds(JavaAccess<?> access) {
        List<JavaMethod> methods = enclosingMethods(access.getOrigin());
        return !methods.isEmpty()
            && methods.stream()
                .allMatch(
                    method ->
                        method.getRawParameterTypes().stream()
                            .anyMatch(type -> type.isEquivalentTo(TxCtx.class)));
      }
    },
    /**
     * The call is made in a lambda of a method that opens a {@link TenantScopedTransaction} scope
     * with {@code execute} or {@code executeNew}, the lambda being the work the scope runs. Read
     * from the bytecode: a call beside the lambda is not under the scope.
     */
    OPENED_AROUND_LAMBDA("makes the call in a lambda of a method that opens a tenant scope") {
      @Override
      boolean holds(JavaAccess<?> access) {
        Class<?> owner = access.getOrigin().getOwner().reflect();
        String method = AllowedCall.enclosingMethodName(access.getOrigin());
        String targetOwner = access.getTarget().getOwner().getName();
        String target = access.getTarget().getName();
        String scopeOwner = TenantScopedTransaction.class.getName();
        try {
          boolean inLambda =
              MethodCallOrder.callsInLambdasOf(owner, method).stream()
                  .anyMatch(call -> call.owner().equals(targetOwner) && call.name().equals(target));
          List<MethodCallOrder.Call> calls = MethodCallOrder.callsInOrder(owner, method);
          return inLambda
              && (MethodCallOrder.firstIndexOf(calls, scopeOwner, "execute").isPresent()
                  || MethodCallOrder.firstIndexOf(calls, scopeOwner, "executeNew").isPresent());
        } catch (IllegalStateException e) {
          return false;
        }
      }
    };

    final String description;

    Scope(String description) {
      this.description = description;
    }

    abstract boolean holds(JavaAccess<?> access);

    private static List<JavaMethod> enclosingMethods(JavaCodeUnit origin) {
      String name = AllowedCall.enclosingMethodName(origin);
      return origin.getOwner().getMethods().stream()
          .filter(method -> method.getName().equals(name))
          .toList();
    }
  }

  /**
   * An allowlist reason that says who calls a method: every access to {@code owner#method} (any
   * overload, a call or a method reference) in the analysed classes comes from one of {@code
   * callers}, and each of them makes it. A claim of no caller says the method is called by nothing,
   * such as an endpoint.
   */
  record CallersClaim(Class<?> owner, String method, List<At> callers) {

    boolean targetedBy(JavaAccess<?> access) {
      return access.getTarget().getOwner().getName().equals(owner.getName())
          && access.getTarget().getName().equals(method);
    }

    String describe() {
      return owner.getSimpleName() + "#" + method;
    }
  }

  /**
   * An allowlist reason that says an endpoint is mapped on the tenant-prefixed route only, where
   * the path pins the tenant: every path of {@code api#method}, class mapping included, starts with
   * the tenant prefix.
   */
  record RouteClaim(Class<?> api, String method) {}

  /**
   * Checks the caller claims in the bytecode, instead of trusting the allowlist reasons that state
   * them: a call from a method a claim does not list is a violation, and so is a listed method that
   * no longer makes the call, or a claim whose owner class was not analysed (a rename would turn
   * the claim into a no-op).
   */
  static ArchRule callersAreOnly(CallersClaim... claims) {
    ArchCondition<JavaClass> onlyListedCallers =
        new ArchCondition<>("call a claimed method only from the callers its allowlist names") {
          private final Set<String> observed = new HashSet<>();
          private final Set<String> analysed = new HashSet<>();

          @Override
          public void check(JavaClass javaClass, ConditionEvents events) {
            analysed.add(javaClass.getName());
            for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
              for (CallersClaim claim : claims) {
                if (!claim.targetedBy(access)) {
                  continue;
                }
                Optional<At> caller =
                    claim.callers().stream()
                        .filter(at -> at.isOrigin(access.getOrigin()))
                        .findFirst();
                if (caller.isPresent() && caller.get().scope() != null) {
                  Scope scope = caller.get().scope();
                  if (scope.holds(access)) {
                    observed.add(claim.describe() + " <- " + caller.get().describe());
                  } else {
                    events.add(
                        SimpleConditionEvent.violated(
                            access,
                            access.getDescription()
                                + " is claimed to run under a tenant scope, but "
                                + caller.get().describe()
                                + " does not ("
                                + scope.description
                                + ")"));
                  }
                } else if (caller.isPresent()) {
                  observed.add(claim.describe() + " <- " + caller.get().describe());
                } else {
                  events.add(
                      SimpleConditionEvent.violated(
                          access,
                          access.getDescription()
                              + " is not one of the callers claimed for "
                              + claim.describe()));
                }
              }
            }
          }

          @Override
          public void finish(ConditionEvents events) {
            for (CallersClaim claim : claims) {
              if (claim.callers().isEmpty() && !analysed.contains(claim.owner().getName())) {
                events.add(
                    new SimpleConditionEvent(
                        claim,
                        false,
                        claim.owner().getSimpleName()
                            + " was not analysed: the claim of no caller for "
                            + claim.describe()
                            + " is not checked"));
              }
              for (At caller : claim.callers()) {
                if (!observed.contains(claim.describe() + " <- " + caller.describe())) {
                  events.add(
                      new SimpleConditionEvent(
                          claim,
                          false,
                          caller.describe()
                              + " is claimed to call "
                              + claim.describe()
                              + " but no such call was found"));
                }
              }
            }
          }
        };
    return classes()
        .should(onlyListedCallers)
        .because(
            "an allowlist reason that names the callers of a method must be true of the bytecode:"
                + " a new caller of a method whose scope is pinned by its known callers would"
                + " reach the contract lookup with no pin");
  }

  /**
   * Checks the route claims: the claimed method has a request mapping and every path it is served
   * on, the class mapping included, starts with the tenant prefix. A claim whose class was not
   * analysed is a violation too.
   */
  static ArchRule mappedOnTheTenantRouteOnly(RouteClaim... claims) {
    ArchCondition<JavaClass> tenantRoutes =
        new ArchCondition<>("map each claimed endpoint on the tenant-prefixed route only") {
          private final Set<RouteClaim> seen = new HashSet<>();

          @Override
          public void check(JavaClass javaClass, ConditionEvents events) {
            for (RouteClaim claim : claims) {
              if (claim.api().getName().equals(javaClass.getName())) {
                seen.add(claim);
                routeViolation(claim)
                    .ifPresent(
                        message -> events.add(SimpleConditionEvent.violated(javaClass, message)));
              }
            }
          }

          @Override
          public void finish(ConditionEvents events) {
            for (RouteClaim claim : claims) {
              if (!seen.contains(claim)) {
                events.add(
                    new SimpleConditionEvent(
                        claim,
                        false,
                        claim.api().getSimpleName()
                            + " was not analysed: the route of "
                            + claim.method()
                            + " is not checked"));
              }
            }
          }
        };
    return classes()
        .should(tenantRoutes)
        .because(
            "an allowlist reason that says an endpoint is on the tenant-prefixed route only relies"
                + " on the path to pin the tenant: a short route beside it would reach the lookup"
                + " with a scope of several tenants");
  }

  private static Optional<String> routeViolation(RouteClaim claim) {
    String where = claim.api().getSimpleName() + "#" + claim.method();
    Method endpoint =
        Arrays.stream(claim.api().getDeclaredMethods())
            .filter(method -> method.getName().equals(claim.method()))
            .findFirst()
            .orElse(null);
    if (endpoint == null) {
      return Optional.of(where + " does not exist");
    }
    RequestMapping mapping =
        AnnotatedElementUtils.findMergedAnnotation(endpoint, RequestMapping.class);
    if (mapping == null || mapping.path().length == 0) {
      return Optional.of(where + " has no mapping path");
    }
    RequestMapping classMapping =
        AnnotatedElementUtils.findMergedAnnotation(claim.api(), RequestMapping.class);
    String[] prefixes =
        classMapping != null && classMapping.path().length > 0
            ? classMapping.path()
            : new String[] {""};
    for (String prefix : prefixes) {
      for (String path : mapping.path()) {
        if (!(prefix + path).startsWith(TenantUriUtils.TENANT_PREFIX)) {
          return Optional.of(where + " is also served on " + prefix + path);
        }
      }
    }
    return Optional.empty();
  }

  /** Allowed access of {@code caller}'s {@code method} to the query or wrapper {@code target}. */
  private static AllowedCall allow(Class<?> caller, String method, String target, String reason) {
    return new AllowedCall(caller, method, target, reason);
  }

  private static boolean isTenantlessQuery(
      JavaAccess<?> access, Set<Method> tenantlessServiceMethods) {
    if (!(access.getTarget() instanceof AccessTarget.CodeUnitAccessTarget codeUnit)) {
      return false;
    }
    Method method = reflectedMethod(codeUnit);
    if (method == null) {
      return false;
    }
    String owner = codeUnit.getOwner().getFullName();
    if (owner.equals(InjectorContractRepository.class.getName())) {
      return isTenantlessRepositoryQuery(method);
    }
    return owner.equals(InjectorContractService.class.getName())
        && tenantlessServiceMethods.contains(method);
  }

  private static Method reflectedMethod(AccessTarget.CodeUnitAccessTarget target) {
    try {
      Class<?>[] parameterTypes =
          target.getRawParameterTypes().stream().map(JavaClass::reflect).toArray(Class<?>[]::new);
      return target.getOwner().reflect().getMethod(target.getName(), parameterTypes);
    } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
      return null;
    }
  }

  private static boolean isTenantlessRepositoryQuery(Method method) {
    if (method.getDeclaringClass().equals(CrudRepository.class)) {
      return method.getParameterCount() == 0;
    }
    return Arrays.stream(method.getParameters()).noneMatch(p -> carriesTenant(p));
  }

  private static boolean carriesTenant(Parameter parameter) {
    Class<?> type = parameter.getType();
    if (type.equals(InjectorContract.class)
        || type.equals(InjectorContractId.class)
        || type.equals(Tenant.class)
        || type.equals(TxCtx.class)) {
      return true;
    }
    Param param = parameter.getAnnotation(Param.class);
    String name = param != null ? param.value() : parameter.getName();
    return name.toLowerCase(Locale.ROOT).contains("tenant");
  }

  /**
   * The public {@link InjectorContractService} methods that take no tenant argument and reach a
   * tenant-less repository query, directly or through a method of the same class.
   */
  private static Set<Method> tenantlessInjectorContractServiceMethods() {
    JavaClass service = new ClassFileImporter().importClass(InjectorContractService.class);
    Set<Method> wrappers = new HashSet<>();
    for (JavaMethod method : service.getMethods()) {
      Method reflected = method.reflect();
      if (Modifier.isPublic(reflected.getModifiers())
          && Arrays.stream(reflected.getParameters()).noneMatch(p -> carriesTenant(p))
          && reachesTenantlessRepositoryQuery(method, new HashSet<>())) {
        wrappers.add(reflected);
      }
    }
    return wrappers;
  }

  private static boolean reachesTenantlessRepositoryQuery(
      JavaMethod method, Set<JavaMethod> visited) {
    if (!visited.add(method)) {
      return false;
    }
    for (JavaAccess<?> access : method.getAccessesFromSelf()) {
      if (!(access.getTarget() instanceof AccessTarget.CodeUnitAccessTarget codeUnit)) {
        continue;
      }
      if (codeUnit.getOwner().isEquivalentTo(InjectorContractRepository.class)) {
        Method reflected = reflectedMethod(codeUnit);
        if (reflected != null && isTenantlessRepositoryQuery(reflected)) {
          return true;
        }
      } else if (codeUnit.getOwner().isEquivalentTo(InjectorContractService.class)) {
        Optional<? extends JavaCodeUnit> callee = codeUnit.resolveMember();
        if (callee.isPresent()
            && callee.get() instanceof JavaMethod calleeMethod
            && reachesTenantlessRepositoryQuery(calleeMethod, visited)) {
          return true;
        }
      }
    }
    return false;
  }
}
