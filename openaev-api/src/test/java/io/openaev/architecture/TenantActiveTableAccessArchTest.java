package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.openaev.database.model.Vulnerability;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.CweRepository;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.database.repository.LessonsTemplateRepository;
import io.openaev.database.repository.MitigationRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.processor.datapack.V20260330_Default_tenant_data;
import io.openaev.rest.atomic_testing.AtomicTestingApi;
import io.openaev.rest.collector.CollectorApi;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.exercise.ExerciseApi;
import io.openaev.rest.exercise.ExerciseImportApi;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.InjectApi;
import io.openaev.rest.inject.ScenarioInjectApi;
import io.openaev.rest.inject.SimulationInjectApi;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.ScenarioInjectService;
import io.openaev.rest.inject_expectation_trace.InjectExpectationTraceApi;
import io.openaev.rest.lessons.ExerciseLessonsApi;
import io.openaev.rest.lessons.ScenarioLessonsApi;
import io.openaev.rest.lessons_template.LessonsTemplateApi;
import io.openaev.rest.mapper.MapperApi;
import io.openaev.rest.mitigation.MitigationApi;
import io.openaev.rest.payload.PayloadApi;
import io.openaev.rest.payload.service.PayloadUpsertService;
import io.openaev.rest.scenario.ScenarioApi;
import io.openaev.rest.scenario.ScenarioImportApi;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.service.InjectExpectationTraceService;
import io.openaev.service.MapperService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService;
import io.openaev.service.attackpath.ingestion.AttackPathFindingIngestionService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.telemetry.metric_collectors.InventoryMetricCollector;
import io.openaev.telemetry.metric_collectors.ProductInventoryMetricCollector;
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
          "attackpath_execution",
          "attackpath_finding");

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
              // Waived INSERT-only provisioning writer (VALUES insert + TenantContext attribution,
              // pinned by CweHttpIsolationTest; conversion to the primitive is tracked):
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
              // Documented degraded background reader (telemetry counts read 0 rows unscoped):
              InventoryMetricCollector.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(CollectorRepository.class)
          .because(
              "collectors is tenant-active: an accessor without a tenant scope silently reads zero"
                  + " rows. New accessors must carry a scope and be allowlisted here");

  @ArchTest
  static final ArchRule attackpath_execution_repository_access_is_reviewed =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              // Read path, driven by the TxCtx-carrying AttackPathApi (pinned by
              // TenantScopedEntrypointsTxCtxArchTest):
              AttackPathGraphService.class,
              // Background writer, scoped: opens its own transaction through the tenant primitive
              // with the inject's tenant, and stamps the row through TenantWriteScopeResolver.
              // Pinned by AttackPathIngestionTenantAttributionTest:
              AttackPathExecutionIngestionService.class,
              // Scoped reader: reads the step's execution rows inside its own executeNew (the
              // inject's tenant) with an explicit tenantId predicate, to attribute copied findings.
              // Pinned by AttackPathFindingIngestionServiceTest:
              AttackPathFindingIngestionService.class)
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
}
