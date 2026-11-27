package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.openaev.architecture.BackgroundEntrypointTenantScopeArchTest;
import io.openaev.database.model.DualScopeBase;
import io.openaev.database.model.TenantBase;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.TestExecutionListeners;

/**
 * Covers the tenant filtering wiring: tables derived from the live schema (every table with a
 * {@code tenant_id} column, classified by nullability, including join tables an entity scan would
 * miss), the inspector registered with Hibernate and gating every tenant table but the
 * self-isolated ones, and the model/schema coherence check that there is no gap versus what the
 * entity markers protect.
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("Tenant filtering wiring and model/schema coherence")
class TenantFilteringConfigTest {

  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private ScopeStatementInspector inspector;

  @Test
  @DisplayName("classifies tenant tables by the nullability of their tenant_id column")
  void classifiesByNullability() {
    TenantTables tables = TenantFilteringConfig.deriveFromSchema(dataSource);
    assertEquals(TenantTables.Family.STRICT, tables.family("documents"), "NOT NULL tenant_id");
    assertEquals(TenantTables.Family.DUAL, tables.family("groups"), "nullable tenant_id");
    assertEquals(TenantTables.Family.DUAL, tables.family("parameters"), "nullable tenant_id");
  }

  @Test
  @DisplayName("covers join tables that carry a tenant_id but have no entity class")
  void coversJoinTablesWithoutEntities() {
    TenantTables tables = TenantFilteringConfig.deriveFromSchema(dataSource);
    assertEquals(TenantTables.Family.STRICT, tables.family("users_tenants"));
    assertEquals(TenantTables.Family.STRICT, tables.family("injectors_contracts_attack_patterns"));
  }

  @Test
  @DisplayName("the gated set of the arch guards equals what production gates from the schema")
  void backgroundGuardGatedTablesMatchProductionSchema() {
    // The arch guards have no database: they derive the gated tables from the entity model and
    // union a checked-in inventory of the strict join tables an entity scan misses. Production
    // derives the same set from the live schema. If the two ever diverge (a new tenant_id join
    // table lands, or the inventory lists something bogus) every guard built on that set judges
    // the wrong tables. This assertion is the honesty check named by the inventory file.
    TenantTables gatedBySchema =
        TenantFilteringConfig.deriveFromSchema(dataSource).withoutSelfIsolated();
    Set<String> fromSchema = new TreeSet<>(gatedBySchema.strict());
    fromSchema.addAll(gatedBySchema.dualScope());
    Set<String> fromGuard = BackgroundEntrypointTenantScopeArchTest.gatedTenantTables();
    assertEquals(
        fromSchema,
        fromGuard,
        "the arch guards' gated tables must match the live schema; update"
            + " tenant-strict-tables-missed-by-entity-scan.txt to match");
  }

  @Test
  @DisplayName("a table without a tenant_id column is not in the derived set")
  void nonTenantTableExcluded() {
    assertEquals(
        TenantTables.Family.NONE,
        TenantFilteringConfig.deriveFromSchema(dataSource).family("users"));
  }

  @Test
  @DisplayName("the inspector is the one Hibernate runs (guards against another one displacing it)")
  void inspectorIsRegisteredWithHibernate() {
    // Guards the putIfAbsent trade-off: if any other statement_inspector were ever installed ahead
    // of ours, isolation would silently switch off. This assertion fails the build instead.
    assertSame(
        inspector,
        entityManagerFactory
            .unwrap(SessionFactoryImplementor.class)
            .getSessionFactoryOptions()
            .getStatementInspector());
  }

  @Test
  @DisplayName(
      "given the wired inspector, should gate a tenant table and leave a self-isolated one ungated")
  void given_wiredInspector_should_gateATenantTableAndLeaveASelfIsolatedOneUngated() {
    // Arrange: no activation property is set on this run, so the wired bean is the only input
    String gated = "SELECT * FROM documents d WHERE d.document_id = ?";
    String selfIsolated = "SELECT * FROM users_tenants ut WHERE ut.user_id = ?";

    // Act & Assert: the marking dimension is independent of the tenant one, so the assertion is
    // on the tenant predicate alone
    assertTrue(inspector.inspect(gated).contains("can_access_tenant"), gated);
    assertFalse(inspector.inspect(selfIsolated).contains("can_access_tenant"), selfIsolated);
  }

  @Test
  @DisplayName(
      "every tenant-marked entity is covered by the schema with the same family (no v1 hole)")
  void markedEntitiesAgreeWithSchema() {
    Set<Class<?>> marked =
        entityManagerFactory.getMetamodel().getEntities().stream()
            .map(entity -> (Class<?>) entity.getJavaType())
            .filter(
                type ->
                    TenantBase.class.isAssignableFrom(type)
                        || DualScopeBase.class.isAssignableFrom(type))
            .collect(Collectors.toSet());

    TenantTables fromModel = TenantTables.fromEntities(marked);
    TenantTables fromSchema = TenantFilteringConfig.deriveFromSchema(dataSource);

    // A marked table missing here is either absent from the schema (tenant_id column forgotten) or
    // classified differently (marker says dual but the column is NOT NULL, or the reverse).
    Set<String> strictGap = new HashSet<>(fromModel.strict());
    strictGap.removeAll(fromSchema.strict());
    Set<String> dualGap = new HashSet<>(fromModel.dualScope());
    dualGap.removeAll(fromSchema.dualScope());

    assertTrue(
        strictGap.isEmpty(), "strict-marked entities not strict in the schema: " + strictGap);
    assertTrue(dualGap.isEmpty(), "dual-marked entities not dual in the schema: " + dualGap);

    // Guard against a weak pass: a near-empty metamodel enumeration would make the subset checks
    // trivially true. The model has ~38 tenant-aware entities across both families.
    assertTrue(
        marked.size() >= 20,
        "expected the metamodel to expose the tenant entities, got " + marked.size());
    assertTrue(fromModel.strict().contains("documents"), "sanity: a known strict table is present");
    assertTrue(fromModel.dualScope().contains("groups"), "sanity: a known dual table is present");
  }

  /**
   * The 80 tables the first rollout named one by one. Frozen here so that every one of them stays
   * gated: the schema these tests run on is the one Flyway builds, the same one production runs on.
   * A later tenant table is gated from the day it exists and is not named here, so this list must
   * never be extended.
   */
  private static final Set<String> TABLES_NAMED_BY_THE_ROLLOUT =
      Set.of(
          "agents",
          "asset_agent_jobs",
          "asset_groups",
          "assets",
          "attackpath_execution",
          "attackpath_execution_collector",
          "attackpath_execution_remediation",
          "attackpath_finding",
          "attack_patterns",
          "autonomous_directives",
          "autonomous_events",
          "autonomous_objective_templates",
          "autonomous_runs",
          "catalog_items",
          "catalog_releases",
          "challenges",
          "channels",
          "collectors",
          "collector_types",
          "conditions",
          "connector_instances",
          "content_changes",
          "custom_dashboards",
          "custom_domains",
          "cwes",
          "datapacks",
          "documents",
          "domains",
          "execution_traces",
          "executors",
          "exercises",
          "findings",
          "groups",
          "import_mappers",
          "injector_contract_tags",
          "injectors",
          "injectors_contracts",
          "injectors_contracts_attack_patterns",
          "injectors_contracts_domains",
          "injectors_contracts_vulnerabilities",
          "injectors_injector_contracts",
          "injects",
          "kill_chain_phases",
          "lessons_templates",
          "marking_definitions",
          "mitigations",
          "notification_events",
          "notifications",
          "notification_triggers",
          "notifiers",
          "organizations",
          "parameters",
          "payload_identities",
          "payload_revocations",
          "payloads",
          "phishing_email_templates",
          "phishing_landing_pages",
          "phishing_results",
          "reporting_generations",
          "reportings",
          "reporting_schedules",
          "roles",
          "scenario_identities",
          "scenario_revisions",
          "scenarios",
          "scope_variables",
          "secret_references",
          "secrets",
          "security_coverages",
          "steps",
          "steps_delay_queue",
          "tag_rules",
          "tags",
          "teams",
          "tenant_xtmhub_registrations",
          "vulnerabilities",
          "widgets",
          "workflows",
          "workflow_scope_rules",
          "workflow_states");

  @Test
  @DisplayName("given the live schema, should gate every table the rollout named")
  void given_liveSchema_should_gateEveryTableTheRolloutNamed() {
    // Arrange
    TenantTables schema = TenantFilteringConfig.deriveFromSchema(dataSource);

    // Act
    TenantTables armed = TenantFilteringConfig.arm(schema);
    Set<String> armedFromSchema = new TreeSet<>(armed.strict());
    armedFromSchema.addAll(armed.dualScope());

    // Assert
    assertEquals(80, TABLES_NAMED_BY_THE_ROLLOUT.size(), "the frozen list is the 80 tables");
    assertTrue(
        armedFromSchema.containsAll(TABLES_NAMED_BY_THE_ROLLOUT),
        "the live schema must gate every table the rollout named; missing: "
            + missingFrom(armedFromSchema));
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.gatedTenantTables()
            .containsAll(TABLES_NAMED_BY_THE_ROLLOUT),
        "the arch guards' DB-less gated set must hold every table of the same list");
  }

  private static Set<String> missingFrom(Set<String> armed) {
    Set<String> missing = new TreeSet<>(TABLES_NAMED_BY_THE_ROLLOUT);
    missing.removeAll(armed);
    return missing;
  }

  @Nested
  @DisplayName("The startup bean")
  class StartupBean {

    private final Logger logger = (Logger) LoggerFactory.getLogger(TenantFilteringConfig.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level configuredLevel;

    // The test profile logs at ERROR once a Spring context has started in the JVM; the level is
    // pinned here so the assertions read the WARN whatever ran before.
    @BeforeEach
    void attach() {
      configuredLevel = logger.getLevel();
      logger.setLevel(Level.WARN);
      appender.start();
      logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
      logger.detachAppender(appender);
      logger.setLevel(configuredLevel);
    }

    @Test
    @DisplayName("given no retired property, should gate the anchors and log nothing")
    void given_noRetiredProperty_should_gateTheAnchorsAndLogNothing() {
      // Act
      TenantTables armed =
          new TenantFilteringConfig().tenantTables(dataSource, new MockEnvironment());

      // Assert
      assertTrue(
          armed.strict().containsAll(Set.of("injects", "exercises", "scenarios", "findings")));
      assertTrue(armed.dualScope().contains("roles"));
      assertFalse(armed.strict().contains("users_tenants"), "a self-isolated table is never gated");
      assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName(
        "given the retired property set, should log one WARN naming it and gate the same tables")
    void given_retiredPropertySet_should_logOneWarnNamingItAndGateTheSameTables() {
      // Arrange
      TenantTables withoutProperty =
          new TenantFilteringConfig().tenantTables(dataSource, new MockEnvironment());
      MockEnvironment environment =
          new MockEnvironment()
              .withProperty(TenantFilteringConfig.RETIRED_ACTIVE_TABLES_PROPERTY, "tags");

      // Act
      TenantTables withProperty = new TenantFilteringConfig().tenantTables(dataSource, environment);

      // Assert
      assertEquals(withoutProperty, withProperty);
      assertThat(appender.list)
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                    .contains(TenantFilteringConfig.RETIRED_ACTIVE_TABLES_PROPERTY);
              });
    }
  }
}
