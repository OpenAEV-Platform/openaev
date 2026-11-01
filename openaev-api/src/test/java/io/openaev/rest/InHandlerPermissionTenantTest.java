package io.openaev.rest;

import static io.openaev.api.attackpath.AttackPathApi.ATTACK_PATH_URI;
import static io.openaev.api.autonomous.AutonomousRunApi.AUTONOMOUS_URI;
import static io.openaev.api.groups.TenantGroupApi.GROUP_URI;
import static io.openaev.api.notifier.NotifierApi.NOTIFIER_URI;
import static io.openaev.api.notifier.NotifierApi.TENANT_NOTIFIER_URI;
import static io.openaev.rest.asset.endpoint.EndpointApi.ASSET_URI;
import static io.openaev.rest.reporting.ReportingApi.REPORTINGS_URI;
import static io.openaev.rest.reporting.ReportingApi.TENANT_REPORTINGS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.asset.dto.AssetUpdateMarkingsInput;
import io.openaev.api.groups.dto.GroupUpdateMarkingsInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.NotifierType;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ReportingFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.ReportingComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A permission decision a handler takes in its own code holds in the tenant of the rows it returns.
 *
 * <p>The caller holds the rights {@code @WithMockUser} grants in the default tenant, and is a
 * member of tenant B, where it holds only what a test grants there. The requests select B with the
 * {@code X-Tenant-Ids} header: the rows come from B while the ambient tenant stays the default one.
 * A decision taken in the ambient tenant would therefore answer with the default tenant's rights on
 * B's rows. The member contrast grants the same rights in B and expects the rows back.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("In-handler permission decisions hold in the tenant of the rows they return")
class InHandlerPermissionTenantTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ReportingComposer reportingComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private MarkingClearanceCacheManager clearanceCache;

  // The autonomous run endpoints are EE-gated; the mock's license checks answer "active".
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    reportingComposer.reset();
    endpointComposer.reset();
    markingDefinitionComposer.reset();
    tenantB = tenantHelper.createTenantWithCurrentUser("in-handler-permission-b").getId();
  }

  @Nested
  @DisplayName("Notifier configuration")
  @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
  class NotifierConfiguration {

    @Test
    @DisplayName("given the right only outside B should list B's notifier without configuration")
    void given_rightOnlyOutsideB_should_listBNotifierWithoutConfiguration() throws Exception {
      // -- ARRANGE --
      String notifierId = seedNotifier(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(NOTIFIER_URI)))
          .andExpect(status().isOk())
          .andExpect(jsonPath(notifierUrl(notifierId)).doesNotExist());
    }

    @Test
    @DisplayName("given the right only outside B should read B's notifier without configuration")
    void given_rightOnlyOutsideB_should_readBNotifierWithoutConfiguration() throws Exception {
      // -- ARRANGE --
      String notifierId = seedNotifier(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(NOTIFIER_URI + "/{notifierId}", notifierId)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.notifier_configuration").doesNotExist());
    }

    @Test
    @DisplayName("given the right only outside B should search B's notifier without configuration")
    void given_rightOnlyOutsideB_should_searchBNotifierWithoutConfiguration() throws Exception {
      // -- ARRANGE --
      String notifierId = seedNotifier(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inB(post(NOTIFIER_URI + "/search")).content(defaultSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content" + notifierUrl(notifierId).substring(1)).doesNotExist());
    }

    @Test
    @DisplayName("given the right only outside B should hide the configuration on B's own route")
    void given_rightOnlyOutsideB_should_hideConfigurationOnBRoute() throws Exception {
      // -- ARRANGE --
      String notifierId = seedNotifier(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(get(TENANT_NOTIFIER_URI, tenantB))
          .andExpect(status().isOk())
          .andExpect(jsonPath(notifierUrl(notifierId)).doesNotExist());
    }

    @Test
    @DisplayName("given the right in B should list B's notifier with its configuration")
    void given_rightInB_should_listBNotifierWithConfiguration() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.ACCESS_TENANT_SETTINGS);
      String notifierId = seedNotifier(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(NOTIFIER_URI)))
          .andExpect(status().isOk())
          .andExpect(jsonPath(notifierUrl(notifierId)).exists());
    }

    private String notifierUrl(String notifierId) {
      return "$[?(@.notifier_id == '" + notifierId + "')].notifier_configuration.url";
    }
  }

  @Nested
  @DisplayName("Autonomous run list")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  class AutonomousRunList {

    @Test
    @DisplayName("given the right only outside B should not list B's run")
    void given_rightOnlyOutsideB_should_notListBRun() throws Exception {
      // -- ARRANGE --
      String runId = seedRun(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(AUTONOMOUS_URI)))
          .andExpect(status().isOk())
          .andExpect(jsonPath(runPath(runId)).doesNotExist());
    }

    @Test
    @DisplayName("given the right in B should list B's run")
    void given_rightInB_should_listBRun() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.ACCESS_ASSESSMENT);
      String runId = seedRun(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(AUTONOMOUS_URI)))
          .andExpect(status().isOk())
          .andExpect(jsonPath(runPath(runId)).exists());
    }

    private String runPath(String runId) {
      return "$[?(@.autonomous_run_id == '" + runId + "')]";
    }
  }

  @Nested
  @DisplayName("Reporting subject access")
  @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS, Capability.ACCESS_ASSESSMENT})
  class ReportingSubjectAccess {

    @Test
    @DisplayName("given the subject right only outside B should refuse B's reporting")
    void given_subjectRightOnlyOutsideB_should_refuseBReporting() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.ACCESS_REPORTINGS);
      String reportingId = seedSimulationReporting(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(REPORTINGS_URI + "/{reportingId}", reportingId)))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given the subject right only outside B should not search B's reporting")
    void given_subjectRightOnlyOutsideB_should_notSearchBReporting() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.ACCESS_REPORTINGS);
      String reportingId = seedSimulationReporting(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inB(post(REPORTINGS_URI + "/search")).content(defaultSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(reportingPath(reportingId)).doesNotExist());
    }

    @Test
    @DisplayName("given the subject right only outside B should refuse B's reportings of a subject")
    void given_subjectRightOnlyOutsideB_should_refuseBReportingsOfSubject() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.ACCESS_REPORTINGS);
      seedSimulationReporting(tenantB);
      String subjectId = reportingComposer.generatedItems.getFirst().getContextId();

      // -- ACT & ASSERT --
      mvc.perform(
              inB(
                  get(
                      REPORTINGS_URI + "/context/{contextType}/{contextId}",
                      ReportingContextType.SIMULATION,
                      subjectId)))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given the subject right in B should read and search B's reporting")
    void given_subjectRightInB_should_readAndSearchBReporting() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.ACCESS_REPORTINGS, Capability.ACCESS_ASSESSMENT);
      String reportingId = seedSimulationReporting(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(REPORTINGS_URI + "/{reportingId}", reportingId)))
          .andExpect(status().isOk());
      mvc.perform(inB(post(REPORTINGS_URI + "/search")).content(defaultSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(reportingPath(reportingId)).exists());
    }

    @Test
    @DisplayName("given the subject right only outside B should refuse B's reporting on B's route")
    void given_subjectRightOnlyOutsideB_should_refuseBReportingOnBRoute() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.ACCESS_REPORTINGS);
      String reportingId = seedSimulationReporting(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(get(TENANT_REPORTINGS_URI + "/{reportingId}", tenantB, reportingId))
          .andExpect(status().isNotFound());
    }

    private String reportingPath(String reportingId) {
      return "$.content[?(@.reporting_id == '" + reportingId + "')]";
    }
  }

  @Nested
  @DisplayName("Attack path simulation access")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  class AttackPathSimulationAccess {

    @Test
    @DisplayName("given the right only outside B should refuse a simulation graph read in B")
    void given_rightOnlyOutsideB_should_refuseGraphReadInB() throws Exception {
      // -- ACT & ASSERT --
      mvc.perform(inB(get(graphUri(), UUID.randomUUID().toString())))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given the right in B should read a simulation graph in B")
    void given_rightInB_should_readGraphInB() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.ACCESS_ASSESSMENT);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(graphUri(), UUID.randomUUID().toString()))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("given the right only outside B should not list B's simulation in the picker")
    void given_rightOnlyOutsideB_should_notListBSimulationInPicker() throws Exception {
      // -- ARRANGE --
      String simulationId = seedExecution(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(ATTACK_PATH_URI + "/simulations")))
          .andExpect(status().isOk())
          .andExpect(jsonPath(pickerPath(simulationId)).doesNotExist());
    }

    @Test
    @DisplayName("given the right in B should list B's simulation in the picker")
    void given_rightInB_should_listBSimulationInPicker() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.ACCESS_ASSESSMENT);
      String simulationId = seedExecution(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(ATTACK_PATH_URI + "/simulations")))
          .andExpect(status().isOk())
          .andExpect(jsonPath(pickerPath(simulationId)).exists());
    }

    @Test
    @DisplayName("given the right only outside B should still list the default tenant's simulation")
    void given_rightOnlyOutsideB_should_listDefaultTenantSimulation() throws Exception {
      // -- ARRANGE --
      memberOfDefaultTenant();
      String ofDefault = seedExecution(Tenant.DEFAULT_TENANT_UUID);
      String ofB = seedExecution(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(withoutSelector(get(ATTACK_PATH_URI + "/simulations")))
          .andExpect(status().isOk())
          .andExpect(jsonPath(pickerPath(ofDefault)).exists())
          .andExpect(jsonPath(pickerPath(ofB)).doesNotExist());
    }

    @Test
    @DisplayName("given the right in both tenants should list both simulations")
    void given_rightInBothTenants_should_listBothSimulations() throws Exception {
      // -- ARRANGE --
      memberOfDefaultTenant();
      grantInB(Capability.ACCESS_ASSESSMENT);
      String ofDefault = seedExecution(Tenant.DEFAULT_TENANT_UUID);
      String ofB = seedExecution(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(withoutSelector(get(ATTACK_PATH_URI + "/simulations")))
          .andExpect(status().isOk())
          .andExpect(jsonPath(pickerPath(ofDefault)).exists())
          .andExpect(jsonPath(pickerPath(ofB)).exists())
          // The tenant a row is decided in is a read-side key, never an API field.
          .andExpect(jsonPath("$..tenantId").doesNotExist());
    }

    private String graphUri() {
      return ATTACK_PATH_URI + "/simulations/{simulationId}/graph";
    }

    private String pickerPath(String simulationId) {
      return "$[?(@.simulationId == '" + simulationId + "')]";
    }

    // attackpath_execution has no composer: the row is inserted with an explicit tenant_id, as the
    // attack path ingestion tests do.
    private String seedExecution(String tenantId) {
      String simulationId = UUID.randomUUID().toString();
      entityManager
          .createNativeQuery(
              "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
                  + " attackpath_execution_simulation_id, attackpath_execution_source_kind,"
                  + " attackpath_execution_target_kind, attackpath_execution_target_key,"
                  + " attackpath_execution_executed_at)"
                  + " VALUES (?1, ?2, ?3, 'INJECTOR', 'ASSET', 'asset-1', now())")
          .setParameter(1, UUID.randomUUID().toString())
          .setParameter(2, tenantId)
          .setParameter(3, simulationId)
          .executeUpdate();
      return simulationId;
    }
  }

  @Nested
  @DisplayName("Marking assignment")
  @WithMockUser(
      withCapabilities = {
        Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES,
        Capability.MANAGE_ASSETS,
        Capability.DELETE_MARKING_ASSIGNMENT
      })
  class MarkingAssignment {

    @Test
    @DisplayName("given the removal right only outside B should refuse removing a group marking")
    void given_removalRightOnlyOutsideB_should_refuseRemovingGroupMarking() throws Exception {
      // -- ARRANGE --
      Group group = markedGroupInB();

      // -- ACT & ASSERT --
      mvc.perform(inB(put(GROUP_URI + "/{groupId}/markings", group.getId())).content(clearGroup()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given the removal right in B should remove a group marking")
    void given_removalRightInB_should_removeGroupMarking() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.DELETE_MARKING_ASSIGNMENT);
      Group group = markedGroupInB();

      // -- ACT & ASSERT --
      mvc.perform(inB(put(GROUP_URI + "/{groupId}/markings", group.getId())).content(clearGroup()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("given the removal right only outside B should refuse removing an asset marking")
    void given_removalRightOnlyOutsideB_should_refuseRemovingAssetMarking() throws Exception {
      // -- ARRANGE --
      String assetId = markedAssetInB();

      // -- ACT & ASSERT --
      mvc.perform(inB(put(ASSET_URI + "/{assetId}/markings", assetId)).content(clearAsset()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given the removal right in B should remove an asset marking")
    void given_removalRightInB_should_removeAssetMarking() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.DELETE_MARKING_ASSIGNMENT);
      String assetId = markedAssetInB();

      // -- ACT & ASSERT --
      mvc.perform(inB(put(ASSET_URI + "/{assetId}/markings", assetId)).content(clearAsset()))
          .andExpect(status().isOk());
    }

    private String clearGroup() {
      return asJsonString(new GroupUpdateMarkingsInput(List.of()));
    }

    private String clearAsset() {
      return asJsonString(new AssetUpdateMarkingsInput(List.of()));
    }
  }

  // -- HELPERS --

  /** A group of B granting one of B's markings to the caller, which gives it that clearance. */
  private Group markedGroupInB() {
    return groupInB(List.of(markingOfB()), List.of(testUserHolder.get()));
  }

  private Group groupInB(List<MarkingDefinition> markings, List<User> members) {
    Group group = TenantGroupFixture.getGroup("in-handler-" + UUID.randomUUID());
    group.setUsers(new ArrayList<>(members));
    group.setMarkings(new ArrayList<>(markings));
    // The group composer attributes the group to the ambient tenant.
    String previousTenant = TenantContext.getCurrentTenant();
    TenantContext.setCurrentTenant(tenantB);
    try {
      Group saved = tenantGroupComposer.forGroup(group).persist().get();
      entityManager.flush();
      clearanceCache.evictForUser(testUserHolder.get().getId());
      return saved;
    } finally {
      TenantContext.setCurrentTenant(previousTenant);
    }
  }

  /** An asset of B carrying the marking the caller is cleared for through its group in B. */
  private String markedAssetInB() {
    Group group = markedGroupInB();
    return assetInB(new String[] {group.getMarkings().getFirst().getId()});
  }

  private String assetInB(String[] markingIds) {
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setTenant(new Tenant(tenantB));
    endpoint.setMarkingIds(markingIds);
    String assetId = endpointComposer.forEndpoint(endpoint).persist().get().getId();
    entityManager.flush();
    return assetId;
  }

  private MarkingDefinition markingOfB() {
    MarkingDefinition marking =
        markingDefinitionComposer
            .forMarkingDefinition(
                createMarkingDefinition(
                    MarkingDefinition.TYPE_TLP, uniqueDefinition(), 60, "#111111"))
            .withTenantId(tenantB)
            .persist()
            .get();
    // The clearance is read with JDBC and cached: the marking must be flushed and the cache
    // cleared.
    entityManager.flush();
    clearanceCache.evictForUser(testUserHolder.get().getId());
    return marking;
  }

  /**
   * The non-prefixed route with no selector: the scope is the caller's whole membership, so the
   * rows come from the default tenant and from B at once. The ambient tenant is cleared first:
   * fixtures leave one on the test thread, and the request must see production's fallback to the
   * default tenant.
   */
  private MockHttpServletRequestBuilder withoutSelector(MockHttpServletRequestBuilder request) {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return request.contentType(MediaType.APPLICATION_JSON).with(csrf());
  }

  /** The same route selecting B, so the scope names B alone while the ambient tenant stays out. */
  private MockHttpServletRequestBuilder inB(MockHttpServletRequestBuilder request) {
    return withoutSelector(request).header(TENANT_IDS_HEADER, tenantB);
  }

  private void grantInB(Capability... capabilities) {
    tenantHelper.grantCapabilitiesInTenant(tenantB, Set.of(capabilities));
  }

  /**
   * Membership of the default tenant, which {@code @WithMockUser} does not give. Without it the
   * scope of a request with no selector is B alone, and the default tenant's rows are never read.
   * The capabilities stay the ones {@code @WithMockUser} grants: membership is not a right.
   */
  private void memberOfDefaultTenant() {
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
  }

  private String defaultSearch() {
    return asJsonString(PaginationFixture.getDefault().textSearch("").build());
  }

  private String seedSimulationReporting(String tenantId) {
    Reporting reporting =
        ReportingFixture.createReporting(
            "in-handler-" + UUID.randomUUID(),
            ReportingContextType.SIMULATION,
            UUID.randomUUID().toString());
    reporting.setTenant(new Tenant(tenantId));
    String reportingId = reportingComposer.forReporting(reporting).persist().get().getId();
    entityManager.flush();
    return reportingId;
  }

  // notifiers and autonomous_runs have no composer: the rows are inserted with an explicit
  // tenant_id, as the isolation tests of those tables do.
  private String seedNotifier(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO notifiers (notifier_id, notifier_name, notifier_type,"
                + " notifier_configuration, notifier_built_in, tenant_id)"
                + " VALUES (?1, ?2, ?3, CAST(?4 AS jsonb), false, ?5)")
        .setParameter(1, id)
        .setParameter(2, "in-handler-" + id)
        .setParameter(3, NotifierType.WEBHOOK.name())
        .setParameter(4, "{\"url\":\"https://example.org/" + id + "\"}")
        .setParameter(5, tenantId)
        .executeUpdate();
    return id;
  }

  // COMPLETED keeps the read-path reconcile a no-op: the run is bound to no simulation.
  private String seedRun(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id,"
                + " autonomous_run_objective, autonomous_run_status)"
                + " VALUES (:id, :tenant, 'Own the domain', 'COMPLETED')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }
}
