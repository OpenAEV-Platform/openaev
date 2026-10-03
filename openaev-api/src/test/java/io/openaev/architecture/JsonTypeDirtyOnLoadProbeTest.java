package io.openaev.architecture;

import static io.openaev.architecture.JsonTypeDirtyOnLoadCatalog.jsonFieldValues;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.IntegrationTest;
import io.openaev.architecture.JsonTypeDirtyOnLoadCatalog.JsonField;
import io.openaev.config.JsonTypeUpdateOnLoadProbeConfig;
import io.openaev.config.JsonTypeUpdateOnLoadRecorder;
import io.openaev.database.model.AiAttack;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Collector;
import io.openaev.database.model.Command;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Notification;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.Notifier;
import io.openaev.database.model.NotifierType;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PayloadPrerequisite;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingBranding;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.ReportingModule;
import io.openaev.database.model.ReportingModuleType;
import io.openaev.database.model.ReportingSchedule;
import io.openaev.database.model.ReportingSchedulePeriod;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE;
import io.openaev.database.model.StixRefToExternalRef;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.model.Widget;
import io.openaev.database.model.WidgetLayout;
import io.openaev.database.model.attackpath.AttackPathExecutionCollector;
import io.openaev.database.model.autonomous.AutonomousEvent;
import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.database.model.autonomous.AutonomousScopeTarget;
import io.openaev.engine.api.DateHistogramWidget;
import io.openaev.engine.api.HistogramInterval;
import io.openaev.engine.api.WidgetType;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Measures, on the real stack, whether loading an entity makes Hibernate issue an {@code UPDATE} of
 * the row it just read.
 *
 * <p>A field mapped with hypersistence {@link JsonType} is dirty-checked by comparing the loaded
 * value with a snapshot the type builds itself. When that comparison cannot see the two as equal
 * the entity counts as changed after every load, so a plain read flushes an {@code UPDATE}. On a
 * table whose rows are tenant-scoped by the statement inspector that write is rewritten and can
 * match zero rows, so the read fails instead of merely wasting a statement; #8236 is the first
 * instance of that and the control case here.
 *
 * <p>The verdict is data-dependent, not structural: an empty collection, a {@code String} payload
 * or a value type with its own {@code equals} compares equal whatever the mapping, so no static
 * rule decides it. This probe is therefore the standing test, one case per entity that stores a
 * {@link JsonType} field in a tenant-active table, and a case only counts when every such field of
 * the entity it loads carries non-empty data. {@link JsonTypeDirtyOnLoadCoverageTest} is what makes
 * a newly declared field or a newly activated table reach this class instead of shipping
 * unmeasured.
 */
@Transactional
@WithMockUser(isAdmin = true)
@Import(JsonTypeUpdateOnLoadProbeConfig.class)
@DisplayName("JSON-mapped fields of tenant-active tables do not make a read write")
class JsonTypeDirtyOnLoadProbeTest extends IntegrationTest {

  /**
   * The fields this class measures, as {@code Class.field}. {@link JsonTypeDirtyOnLoadCoverageTest}
   * asserts this is exactly the set stored in a tenant-active table, so a new field or a newly
   * activated table fails the build until a case lands here.
   */
  static final Set<String> PROBED_FIELDS =
      Set.of(
          "Payload.expectedSecurityPlatforms",
          "Payload.arguments",
          "Payload.prerequisites",
          "AiAttack.multiTurn",
          "AiAttack.successDetector",
          "Asset.aiTargetConfiguration",
          "Asset.metadata",
          "AssetGroup.dynamicFilter",
          "Collector.state",
          "Notifier.configuration",
          "Notification.content",
          "NotificationTrigger.eventTypes",
          "NotificationTrigger.filters",
          "Reporting.modules",
          "Reporting.branding",
          "ReportingSchedule.recipientEmails",
          "SecurityCoverage.attackPatternRefs",
          "SecurityCoverage.content",
          "SecurityCoverage.vulnerabilitiesRefs",
          "SecurityCoverage.indicatorsRefs",
          "SecurityCoverage.artifactsRefs",
          "Widget.widgetConfiguration",
          "Widget.layout",
          "AttackPathExecutionCollector.alerts",
          "AutonomousRun.scope",
          "AutonomousRun.agentIds",
          "AutonomousRun.agentModes",
          "AutonomousRun.stepMirror",
          "AutonomousRun.eventMirror",
          "AutonomousEvent.data");

  private static final Set<String> EXERCISED = ConcurrentHashMap.newKeySet();
  private static final AtomicInteger CASES_RUN = new AtomicInteger();

  @Autowired private JsonTypeUpdateOnLoadRecorder recorder;
  @Autowired private ObjectMapper objectMapper;

  // -- payloads: the known-positive control, fixed by #8236 --

  @Test
  @DisplayName("given a payload with expected security platforms, when loaded, then no update")
  void given_a_payload_with_json_fields_should_not_update_on_load() {
    Command payload = new Command(UUID.randomUUID().toString(), Command.COMMAND_TYPE, "probe");
    payload.setContent("echo probe");
    payload.setExecutor("PowerShell");
    populatePayloadJsonFields(payload);

    assertNoUpdateOnLoad("payloads", payload, Payload.class);
  }

  @Test
  @DisplayName(
      "given an AI attack payload with a multi-turn configuration, when loaded, then no update")
  void given_an_ai_attack_with_json_fields_should_not_update_on_load() {
    AiAttack payload = new AiAttack(UUID.randomUUID().toString(), AiAttack.AI_ATTACK_TYPE, "probe");
    payload.setContent("probe prompt");
    populatePayloadJsonFields(payload);
    payload.setMultiTurn(nestedMap());
    payload.setSuccessDetector(nestedMap());

    assertNoUpdateOnLoad("payloads", payload, Payload.class);
  }

  // -- the other tenant-active tables that store a JsonType field --

  @Test
  @DisplayName("given an asset with metadata, when loaded, then no update")
  void given_an_asset_with_json_fields_should_not_update_on_load() {
    Asset asset = new Asset();
    asset.setName("probe-asset");
    asset.setCreatedAt(Instant.now());
    asset.setUpdatedAt(Instant.now());
    asset.setTenant(defaultTenant());
    asset.setMetadata(nestedMap());
    asset.setAiTargetConfiguration(nestedMap());

    assertNoUpdateOnLoad("assets", asset, Asset.class);
  }

  @Test
  @DisplayName("given an asset group with a dynamic filter, when loaded, then no update")
  void given_an_asset_group_with_json_fields_should_not_update_on_load() {
    AssetGroup assetGroup = new AssetGroup();
    assetGroup.setName("probe-asset-group");
    assetGroup.setTenant(defaultTenant());
    assetGroup.setDynamicFilter(filterGroup());

    assertNoUpdateOnLoad("asset_groups", assetGroup, AssetGroup.class);
  }

  @Test
  @DisplayName("given a collector with a state, when loaded, then no update")
  void given_a_collector_with_json_fields_should_not_update_on_load() {
    Collector collector = new Collector();
    collector.setId(UUID.randomUUID().toString());
    collector.setName("probe-collector");
    collector.setType("probe-collector");
    collector.setPeriod(60);
    collector.setTenantId(Tenant.DEFAULT_TENANT_UUID);
    ObjectNode state = objectMapper.createObjectNode();
    state.put("cursor", "2026-10-02T00:00:00Z");
    state.putArray("seen").add("a").add("b");
    collector.setState(state);

    assertNoUpdateOnLoad("collectors", collector, Collector.class);
  }

  @Test
  @DisplayName("given a notifier with a configuration, when loaded, then no update")
  void given_a_notifier_with_json_fields_should_not_update_on_load() {
    Notifier notifier = new Notifier();
    notifier.setName("probe-notifier");
    notifier.setType(NotifierType.WEBHOOK);
    notifier.setTenant(defaultTenant());
    notifier.setConfiguration(nestedMap());

    assertNoUpdateOnLoad("notifiers", notifier, Notifier.class);
  }

  @Test
  @DisplayName("given a notification with content, when loaded, then no update")
  void given_a_notification_with_json_fields_should_not_update_on_load() {
    Notification notification = new Notification();
    notification.setName("probe-notification");
    notification.setType(NotificationTriggerType.LIVE);
    notification.setUser(currentUser());
    notification.setTenant(defaultTenant());
    notification.setContent(new ArrayList<>(List.of(nestedMap(), nestedMap())));

    assertNoUpdateOnLoad("notifications", notification, Notification.class);
  }

  @Test
  @DisplayName(
      "given a notification trigger with event types and filters, when loaded, then no update")
  void given_a_notification_trigger_with_json_fields_should_not_update_on_load() {
    NotificationTrigger trigger = new NotificationTrigger();
    trigger.setName("probe-trigger");
    trigger.setType(NotificationTriggerType.LIVE);
    trigger.setOwner(currentUser());
    trigger.setTenant(defaultTenant());
    trigger.setEventTypes(
        new ArrayList<>(
            List.of(NotificationTriggerEventType.CREATE, NotificationTriggerEventType.UPDATE)));
    trigger.setFilters(filterGroup());

    assertNoUpdateOnLoad("notification_triggers", trigger, NotificationTrigger.class);
  }

  @Test
  @DisplayName("given a reporting with modules and branding, when loaded, then no update")
  void given_a_reporting_with_json_fields_should_not_update_on_load() {
    Reporting reporting = reportingWithModules();

    assertNoUpdateOnLoad("reportings", reporting, Reporting.class);
  }

  @Test
  @DisplayName("given a reporting schedule with recipient emails, when loaded, then no update")
  void given_a_reporting_schedule_with_json_fields_should_not_update_on_load() {
    Reporting reporting = reportingWithModules();
    entityManager.persist(reporting);
    ReportingSchedule schedule = new ReportingSchedule();
    schedule.setReporting(reporting);
    schedule.setPeriod(ReportingSchedulePeriod.DAY);
    schedule.setOwner(currentUser());
    schedule.setTenant(defaultTenant());
    schedule.setRecipientEmails(new ArrayList<>(List.of("a@probe.test", "b@probe.test")));

    assertNoUpdateOnLoad("reporting_schedules", schedule, ReportingSchedule.class);
  }

  @Test
  @DisplayName("given a security coverage with stix references, when loaded, then no update")
  void given_a_security_coverage_with_json_fields_should_not_update_on_load() {
    SecurityCoverage coverage = new SecurityCoverage();
    coverage.setExternalId("probe-external-id");
    coverage.setExternalUrl("https://probe.test/coverage");
    coverage.setName("probe-coverage");
    coverage.setScheduling("P1D");
    coverage.setBundleHashMd5("0123456789abcdef0123456789abcdef");
    coverage.setContent("{\"type\":\"bundle\",\"objects\":[]}");
    coverage.setTenant(defaultTenant());
    coverage.setAttackPatternRefs(stixRefs("attack-pattern--1"));
    coverage.setVulnerabilitiesRefs(stixRefs("vulnerability--1"));
    coverage.setIndicatorsRefs(stixRefs("indicator--1"));
    coverage.setArtifactsRefs(stixRefs("artifact--1"));

    assertNoUpdateOnLoad("security_coverages", coverage, SecurityCoverage.class);
  }

  @Test
  @DisplayName("given a widget with a configuration and a layout, when loaded, then no update")
  void given_a_widget_with_json_fields_should_not_update_on_load() {
    Widget widget = new Widget();
    widget.setType(WidgetType.VERTICAL_BAR_CHART);
    widget.setTenant(defaultTenant());
    DateHistogramWidget configuration = new DateHistogramWidget();
    configuration.setTitle("probe-widget");
    configuration.setDateAttribute("base_created_at");
    configuration.setTimeRange(CustomDashboardTimeRange.LAST_QUARTER);
    configuration.setInterval(HistogramInterval.day);
    configuration.setSeries(new ArrayList<>());
    widget.setWidgetConfiguration(configuration);
    widget.setLayout(new WidgetLayout());

    assertNoUpdateOnLoad("widgets", widget, Widget.class);
  }

  @Test
  @DisplayName("given an attack path execution collector with alerts, when loaded, then no update")
  void given_an_attackpath_execution_collector_with_json_fields_should_not_update_on_load() {
    String executionId = seedAttackPathExecution();
    AttackPathExecutionCollector collector = new AttackPathExecutionCollector();
    collector.setId(UUID.randomUUID().toString());
    collector.setTenant(defaultTenant());
    collector.setSimulationId(UUID.randomUUID().toString());
    collector.setExecutionId(executionId);
    collector.setExpectationType("DETECTION");
    collector.setResultStatusLabel("SUCCESS");
    ObjectNode alerts = objectMapper.createObjectNode();
    alerts.putArray("rules").add("rule-1").add("rule-2");
    collector.setAlerts(alerts);

    assertNoUpdateOnLoad(
        "attackpath_execution_collector", collector, AttackPathExecutionCollector.class);
  }

  @Test
  @DisplayName("given an autonomous run with a scope, when loaded, then no update")
  void given_an_autonomous_run_with_json_fields_should_not_update_on_load() {
    AutonomousRun run = autonomousRun();

    assertNoUpdateOnLoad("autonomous_runs", run, AutonomousRun.class);
  }

  @Test
  @DisplayName("given an autonomous event with data, when loaded, then no update")
  void given_an_autonomous_event_with_json_fields_should_not_update_on_load() {
    AutonomousRun run = autonomousRun();
    entityManager.persist(run);
    AutonomousEvent event = new AutonomousEvent();
    event.setTenant(defaultTenant());
    event.setRunId(run.getId());
    event.setSequence(1L);
    event.setType(AutonomousEventType.DECISION);
    event.setTitle("probe-event");
    event.setData("{\"agent_name\":\"probe\",\"status\":\"start\"}");

    assertNoUpdateOnLoad("autonomous_events", event, AutonomousEvent.class);
  }

  /**
   * On a complete run of this class, every field it declares as probed was actually exercised. A
   * declared field that no case populates would otherwise read as measured while nothing measured
   * it. Skipped on a filtered run, where fewer cases execute by construction.
   */
  @AfterAll
  static void every_declared_field_was_exercised() {
    long cases =
        Arrays.stream(JsonTypeDirtyOnLoadProbeTest.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(Test.class))
            .count();
    if (CASES_RUN.get() != cases) {
      return;
    }
    assertEquals(
        new TreeSet<>(PROBED_FIELDS),
        new TreeSet<>(EXERCISED),
        "every field declared in PROBED_FIELDS must be populated by a case of this class");
  }

  // -- the probe itself --

  /**
   * Persists {@code entity}, forgets it, loads it again and flushes: the flush is what a read
   * transaction does on commit, so an {@code UPDATE} here is one a plain read sends in production.
   *
   * <p>Refuses to run unless every {@link JsonType} field of the entity carries non-empty data: an
   * empty collection round-trips whatever the mapping, so a case built on defaults would pass on a
   * defective mapping and prove nothing.
   */
  private void assertNoUpdateOnLoad(String table, Object entity, Class<?> type) {
    recordExercisedFields(entity);
    entityManager.persist(entity);
    entityManager.flush();
    // Read the identifier only now: most of these entities have a generated id, which is null until
    // the insert.
    Object id =
        entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
    entityManager.clear();

    recorder.start();
    Object loaded = entityManager.find(type, id);
    assertNotNull(loaded, "the probe row must be loadable: " + table + " " + id);
    entityManager.flush();
    recorder.stop();

    List<String> updates = recorder.updatesOn(table);
    assertTrue(
        updates.isEmpty(),
        "loading a row of "
            + table
            + " issued "
            + updates.size()
            + " update(s) of that table, so a read writes: "
            + updates
            + ". A JSON-mapped field of "
            + type.getSimpleName()
            + " does not compare equal to its own snapshot; map it natively with"
            + " @JdbcTypeCode(SqlTypes.JSON) or give the stored value type equals and hashCode.");
    CASES_RUN.incrementAndGet();
  }

  /**
   * Checks the entity carries representative data in every {@link JsonType} field it maps, and
   * records those fields as exercised.
   */
  private void recordExercisedFields(Object entity) {
    Map<JsonField, Object> values = jsonFieldValues(entity);
    assertFalse(
        values.isEmpty(),
        entity.getClass().getSimpleName() + " maps no JsonType field, so it needs no probe case");
    values.forEach(
        (field, value) -> {
          assertNotNull(value, field + " must carry data for this case to measure anything");
          if (value instanceof Collection<?> collection) {
            assertFalse(
                collection.isEmpty(),
                field + " must not be empty: an empty collection round-trips whatever the mapping");
          }
          if (value instanceof Map<?, ?> map) {
            assertFalse(
                map.isEmpty(),
                field + " must not be empty: an empty map round-trips whatever the mapping");
          }
          assertTrue(
              PROBED_FIELDS.contains(field.key()),
              field + " is exercised here but not declared in PROBED_FIELDS");
          EXERCISED.add(field.key());
        });
  }

  // -- representative values --

  private Tenant defaultTenant() {
    return entityManager.getReference(Tenant.class, Tenant.DEFAULT_TENANT_UUID);
  }

  private User currentUser() {
    return entityManager.getReference(User.class, testUserHolder.get().getId());
  }

  private void populatePayloadJsonFields(Payload payload) {
    payload.setTenant(defaultTenant());
    payload.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows});
    payload.setSource(Payload.PAYLOAD_SOURCE.MANUAL);
    payload.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    payload.setExpectedSecurityPlatforms(
        new LinkedHashMap<>(
            Map.of(
                EXPECTATION_TYPE.PREVENTION,
                new ArrayList<>(List.of(SECURITY_PLATFORM_TYPE.EDR, SECURITY_PLATFORM_TYPE.XDR)),
                EXPECTATION_TYPE.DETECTION,
                new ArrayList<>(List.of(SECURITY_PLATFORM_TYPE.SIEM)))));
    PayloadArgument argument = new PayloadArgument();
    argument.setType(PrimitiveType.Text);
    argument.setKey("target");
    argument.setDefaultValue("localhost");
    payload.setArguments(new ArrayList<>(List.of(argument)));
    PayloadPrerequisite prerequisite = new PayloadPrerequisite();
    prerequisite.setExecutor("PowerShell");
    prerequisite.setGetCommand("install");
    payload.setPrerequisites(new ArrayList<>(List.of(prerequisite)));
  }

  /** A map whose values are not all of one type, and nested, like a real stored configuration. */
  private Map<String, Object> nestedMap() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("label", "probe");
    value.put("retries", 3);
    value.put("enabled", true);
    value.put("tags", new ArrayList<>(List.of("one", "two")));
    value.put("nested", new LinkedHashMap<>(Map.of("key", "value")));
    return value;
  }

  private Filters.FilterGroup filterGroup() {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey("base_entity");
    filter.setMode(Filters.FilterMode.or);
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setValues(new ArrayList<>(List.of("asset")));
    Filters.FilterGroup group = new Filters.FilterGroup();
    group.setMode(Filters.FilterMode.and);
    group.setFilters(new ArrayList<>(List.of(filter)));
    return group;
  }

  private Set<StixRefToExternalRef> stixRefs(String stixRef) {
    return new HashSet<>(
        List.of(new StixRefToExternalRef(stixRef, new ArrayList<>(List.of("T1234", "T5678")))));
  }

  private Reporting reportingWithModules() {
    Reporting reporting = new Reporting();
    reporting.setName("probe-reporting");
    reporting.setContextType(ReportingContextType.PLATFORM);
    reporting.setTenant(defaultTenant());
    ReportingModule module = new ReportingModule();
    module.setModuleType(ReportingModuleType.CUSTOM_MARKDOWN);
    module.setModuleTitle("probe");
    module.setModuleConfig(new LinkedHashMap<>(Map.of("content", "# probe")));
    reporting.setModules(new ArrayList<>(List.of(module)));
    ReportingBranding branding = new ReportingBranding();
    branding.setPrimaryColor("#001122");
    reporting.setBranding(branding);
    return reporting;
  }

  private AutonomousRun autonomousRun() {
    AutonomousRun run = new AutonomousRun();
    run.setTenant(defaultTenant());
    run.setObjective("probe objective");
    run.setStatus(AutonomousRunStatus.CREATED);
    run.setScope(
        new ArrayList<>(
            List.of(
                new AutonomousScopeTarget("ASSETS", UUID.randomUUID().toString()),
                new AutonomousScopeTarget("TEAMS", UUID.randomUUID().toString()))));
    run.setAgentIds(new ArrayList<>(List.of("agent-a", "agent-b")));
    run.setAgentModes(new LinkedHashMap<>(Map.of("agent-a", "SCOPED", "agent-b", "EXPANSIVE")));
    run.setStepMirror(new LinkedHashMap<>(Map.of("step-sim", "step-scenario")));
    run.setEventMirror(new LinkedHashMap<>(Map.of("event-sim", "event-scenario")));
    return run;
  }

  private String seedAttackPathExecution() {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
                + " attackpath_execution_simulation_id, attackpath_execution_source_kind,"
                + " attackpath_execution_target_kind, attackpath_execution_target_key,"
                + " attackpath_execution_executed_at)"
                + " VALUES (?1, ?2, ?3, ?4, ?5, ?6, now())")
        .setParameter(1, id)
        .setParameter(2, Tenant.DEFAULT_TENANT_UUID)
        .setParameter(3, UUID.randomUUID().toString())
        .setParameter(4, "INJECT")
        .setParameter(5, "ASSET")
        .setParameter(6, "probe-target")
        .executeUpdate();
    return id;
  }
}
