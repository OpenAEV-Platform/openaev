package io.openaev.api.snapshot;

import static io.openaev.api.snapshot.SnapshotObservationApi.TENANT_SNAPSHOT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.snapshot.form.SnapshotSearchInput;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Capability;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Finding;
import io.openaev.database.model.IndexingStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EngineService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link SnapshotObservationApi} (story 7505, §10.5): capability-based
 * authorization (no {@code resourceId}, no {@code skipRBAC}), cursor validation, and the FR29
 * response contract. The {@code BULK_SNAPSHOT_EXPORT} flag is on for the whole class; see {@link
 * SnapshotObservationFeatureFlagApiTest} for the flag-off case.
 */
@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = "openaev.enabled-dev-features=BULK_SNAPSHOT_EXPORT")
@DisplayName("Snapshot observation API integration tests")
class SnapshotObservationApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;
  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private IndexingStatusRepository indexingStatusRepository;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  @BeforeEach
  void resetFixtures() {
    endpointComposer.reset();
    findingComposer.reset();
    injectComposer.reset();
    tenantComposer.reset();
    attackPatternComposer.reset();
    injectorContractComposer.reset();
    injectExpectationComposer.reset();
    exerciseComposer.reset();
    scenarioComposer.reset();
  }

  private static String attackSearchUri(String tenantId) {
    return TENANT_SNAPSHOT_URI.replace("{tenantId}", tenantId) + "/attack-observations/search";
  }

  private static String vulnerabilitySearchUri(String tenantId) {
    return TENANT_SNAPSHOT_URI.replace("{tenantId}", tenantId)
        + "/vulnerability-observations/search";
  }

  private static String base64Of(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Runs a full indexing round and waits for the engine's async refresh, as in Story 1.6's tests.
   */
  private void indexAndWait() throws InterruptedException {
    entityManager.flush();
    entityManager.clear();
    indexingStatusRepository.deleteAll();
    engineService.bulkProcessing(engineContext.getModels().stream());
    Thread.sleep(1_000);
  }

  private AttackPattern newAttackPattern() {
    AttackPattern attackPattern = new AttackPattern();
    String uniqueId = UUID.randomUUID().toString();
    attackPattern.setName("Technique " + uniqueId);
    attackPattern.setExternalId("T" + uniqueId.substring(0, 8));
    attackPattern.setStixId("attack-pattern--" + uniqueId);
    return attackPattern;
  }

  @Nested
  @DisplayName("Authorization")
  class Authorization {

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_SNAPSHOT_OBSERVATION})
    @DisplayName("given_snapshotCapability_should_return200WithContract")
    void given_snapshotCapability_should_return200WithContract() throws Exception {
      // -- ARRANGE --
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "snapshot-authz-ok", Set.of(Capability.ACCESS_SNAPSHOT_OBSERVATION));
      SnapshotSearchInput input = new SnapshotSearchInput(null, null, null, null);

      // -- ACT --
      String response =
          mvc.perform(
                  post(attackSearchUri(tenant.getId()))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT: FR29 contract fields --
      assertThat((List<?>) JsonPath.read(response, "$.observations")).isEmpty();
      assertThat((Boolean) JsonPath.read(response, "$.has_more")).isFalse();
      assertThat((String) JsonPath.read(response, "$.consistency_mode")).isEqualTo("eventual");
      assertThat((String) JsonPath.read(response, "$.snapshot_window_end")).isNotNull();
      assertThat((String) JsonPath.read(response, "$.indexed_through")).isNotNull();
      assertThat((String) JsonPath.read(response, "$.server_time")).isNotNull();
    }

    @Test
    @WithMockUser
    @DisplayName("given_noCapability_should_forbidSearch")
    void given_noCapability_should_forbidSearch() throws Exception {
      // -- ARRANGE --
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("snapshot-authz-forbid");
      SnapshotSearchInput input = new SnapshotSearchInput(null, null, null, null);

      // -- ACT & ASSERT --
      mvc.perform(
              post(attackSearchUri(tenant.getId()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given_noAuthentication_should_return401")
    void given_noAuthentication_should_return401() throws Exception {
      // -- ACT & ASSERT --
      mvc.perform(
              post(attackSearchUri("00000000-0000-0000-0000-000000000000"))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("Cursor validation")
  class CursorValidation {

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_SNAPSHOT_OBSERVATION})
    @DisplayName("given_sinceAndCursorTogether_should_return400")
    void given_sinceAndCursorTogether_should_return400() throws Exception {
      // -- ARRANGE --
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "snapshot-cursor-since-and-cursor", Set.of(Capability.ACCESS_SNAPSHOT_OBSERVATION));
      SnapshotSearchInput input = new SnapshotSearchInput("some-cursor", Instant.now(), null, null);

      // -- ACT & ASSERT --
      mvc.perform(
              post(attackSearchUri(tenant.getId()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_SNAPSHOT_OBSERVATION})
    @DisplayName("given_unsupportedCursorVersion_should_return400")
    void given_unsupportedCursorVersion_should_return400() throws Exception {
      // -- ARRANGE --
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "snapshot-cursor-bad-version", Set.of(Capability.ACCESS_SNAPSHOT_OBSERVATION));
      String cursor =
          base64Of(
              "{\"v\":2,\"tenant\":\""
                  + tenant.getId()
                  + "\",\"ts\":\"2024-01-01T00:00:00Z\",\"id\":\"doc-id\"}");
      SnapshotSearchInput input = new SnapshotSearchInput(cursor, null, null, null);

      // -- ACT & ASSERT --
      mvc.perform(
              post(attackSearchUri(tenant.getId()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_SNAPSHOT_OBSERVATION})
    @DisplayName("given_cursorForAnotherTenant_should_return400")
    void given_cursorForAnotherTenant_should_return400() throws Exception {
      // -- ARRANGE --
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "snapshot-cursor-foreign-tenant", Set.of(Capability.ACCESS_SNAPSHOT_OBSERVATION));
      String cursor =
          base64Of(
              "{\"v\":1,\"tenant\":\"some-other-tenant\",\"ts\":\"2024-01-01T00:00:00Z\",\"id\":\"doc-id\"}");
      SnapshotSearchInput input = new SnapshotSearchInput(cursor, null, null, null);

      // -- ACT & ASSERT --
      mvc.perform(
              post(attackSearchUri(tenant.getId()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("Vulnerability observation stream")
  class VulnerabilityStream {

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_SNAPSHOT_OBSERVATION})
    @DisplayName("given_snapshotCapability_should_return200ForVulnerabilityObservations")
    void given_snapshotCapability_should_return200ForVulnerabilityObservations() throws Exception {
      // -- ARRANGE --
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "snapshot-vuln-ok", Set.of(Capability.ACCESS_SNAPSHOT_OBSERVATION));
      SnapshotSearchInput input = new SnapshotSearchInput(null, null, null, null);

      // -- ACT & ASSERT --
      mvc.perform(
              post(vulnerabilitySearchUri(tenant.getId()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("Tenancy (AC11)")
  class Tenancy {

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_SNAPSHOT_OBSERVATION})
    @DisplayName("given_documentsInTwoTenants_should_onlyReturnTheRequestingTenants")
    void given_documentsInTwoTenants_should_onlyReturnTheRequestingTenants() throws Exception {
      // -- ARRANGE: a vulnerability-observation grain in each of two distinct tenants --
      Tenant tenantA =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "snapshot-tenancy-a", Set.of(Capability.ACCESS_SNAPSHOT_OBSERVATION));
      Tenant tenantB =
          tenantComposer
              .forTenant(TenantFixture.getTenant("snapshot-tenancy-b-" + UUID.randomUUID()))
              .persist()
              .get();

      Asset assetA = EndpointFixture.createEndpoint("ep-tenant-a-" + UUID.randomUUID());
      assetA.setTenant(tenantA);
      entityManager.persist(assetA);
      Inject injectA = InjectFixture.getDefaultInject();
      injectA.setTenant(tenantA);
      entityManager.persist(injectA);
      Finding findingA = FindingFixture.createDefaultCveFindingWithRandomTitle();
      findingA.setTenant(tenantA);
      findingA.setInject(injectA);
      findingA.setAssets(List.of(assetA));
      entityManager.persist(findingA);

      Asset assetB = EndpointFixture.createEndpoint("ep-tenant-b-" + UUID.randomUUID());
      assetB.setTenant(tenantB);
      entityManager.persist(assetB);
      Inject injectB = InjectFixture.getDefaultInject();
      injectB.setTenant(tenantB);
      entityManager.persist(injectB);
      Finding findingB = FindingFixture.createDefaultCveFindingWithRandomTitle();
      findingB.setTenant(tenantB);
      findingB.setInject(injectB);
      findingB.setAssets(List.of(assetB));
      entityManager.persist(findingB);
      entityManager.flush();

      // Outside the default safety_lag (120s): otherwise both grains are excluded from every
      // search regardless of tenant, and the assertion below would fail for the wrong reason.
      Instant outsideSafetyLag = Instant.now().minus(2, ChronoUnit.HOURS);
      bumpFindingTimestamp(findingA.getId(), outsideSafetyLag);
      bumpFindingTimestamp(findingB.getId(), outsideSafetyLag);

      indexAndWait();

      SnapshotSearchInput input = new SnapshotSearchInput(null, null, 100, null);

      // -- ACT --
      String response =
          mvc.perform(
                  post(vulnerabilitySearchUri(tenantA.getId()))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT: only the requesting tenant's asset is ever returned --
      List<String> assetIds = JsonPath.read(response, "$.observations[*].asset_id");
      assertThat(assetIds).contains(assetA.getId());
      assertThat(assetIds).doesNotContain(assetB.getId());
    }

    private void bumpFindingTimestamp(String findingId, Instant ts) {
      entityManager
          .createNativeQuery("UPDATE findings SET finding_updated_at = :ts WHERE finding_id = :id")
          .setParameter("ts", ts)
          .setParameter("id", findingId)
          .executeUpdate();
    }
  }

  @Nested
  @DisplayName("Existence probe (AC7)")
  class ExistenceProbe {

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_SNAPSHOT_OBSERVATION})
    @DisplayName("given_onlyAgentLevelRowPending_should_stillAdvanceIndexedThrough")
    void given_onlyAgentLevelRowPending_should_stillAdvanceIndexedThrough() throws Exception {
      // -- ARRANGE: an agentless parent (indexable) plus an agent-level child on the same inject
      // (excluded by `agent_id IS NULL`, per the AttackObservationRepository selection predicate)
      // --
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "snapshot-ac7", Set.of(Capability.ACCESS_SNAPSHOT_OBSERVATION));

      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(
              EndpointFixture.createEndpoint("ep-ac7-" + UUID.randomUUID()));
      endpointWrapper.persist();
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectorContractComposer.Composer contractWrapper =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withAttackPattern(attackPatternWrapper);

      var parentExpectation =
          InjectExpectationFixture.createExpectationWithTypeAndStatus(
              EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS);
      InjectExpectationComposer.Composer parentWrapper =
          injectExpectationComposer.forExpectation(parentExpectation).withEndpoint(endpointWrapper);

      Agent agent = AgentFixture.createDefaultAgentService();
      agent.setAsset(endpointWrapper.get());
      entityManager.persist(agent);
      entityManager.flush();
      Agent persistedAgent = entityManager.getReference(Agent.class, agent.getId());

      DetectionInjectExpectation childExpectation =
          InjectExpectationFixture.createDefaultDetectionInjectExpectation();
      childExpectation.setAgent(persistedAgent);
      childExpectation.setAsset(endpointWrapper.get());
      childExpectation.setScore(100.0);
      InjectExpectationComposer.Composer childWrapper =
          injectExpectationComposer.forExpectation(childExpectation);

      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(contractWrapper)
              .withExpectation(parentWrapper)
              .withExpectation(childWrapper);

      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .persist()
              .get();
      Exercise exercise = ExerciseFixture.createDefaultIncidentResponseExercise(Instant.now());
      exercise.setScenario(scenario);
      exerciseComposer.forExercise(exercise).withInject(injectWrapper).persist();

      // Attribute the whole grain to the requesting tenant.
      endpointWrapper.get().setTenant(tenant);
      injectWrapper.get().setTenant(tenant);
      entityManager.flush();

      Instant now = Instant.now();
      // Outside the default safety_lag (120s), so the probe is not skipped; before the parent's
      // own timestamp, so the parent is not itself "pending".
      Instant cursor = now.minus(2, ChronoUnit.HOURS);
      bumpTimestamp(parentWrapper.get().getId(), cursor.minusSeconds(60));
      // Inside (cursor, now - grace]: the only window a pending row could be found in.
      bumpTimestamp(childWrapper.get().getId(), now.minusSeconds(90));

      IndexingStatus indexingStatus = new IndexingStatus();
      indexingStatus.setType("snapshot-attack-observation");
      indexingStatus.setLastIndexing(cursor);
      indexingStatusRepository.save(indexingStatus);

      entityManager.flush();
      entityManager.clear();

      SnapshotSearchInput input = new SnapshotSearchInput(null, null, null, null);

      // -- ACT --
      String response =
          mvc.perform(
                  post(attackSearchUri(tenant.getId()))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT: the agent-level row must not be mistaken for genuine pending work --
      Instant indexedThrough = Instant.parse((String) JsonPath.read(response, "$.indexed_through"));
      Instant serverTime = Instant.parse((String) JsonPath.read(response, "$.server_time"));
      assertThat(indexedThrough).isEqualTo(serverTime.minusSeconds(60));
    }

    private void bumpTimestamp(String expectationId, Instant ts) {
      entityManager
          .createNativeQuery(
              "UPDATE injects_expectations SET inject_expectation_updated_at = :ts"
                  + " WHERE inject_expectation_id = :id")
          .setParameter("ts", ts)
          .setParameter("id", expectationId)
          .executeUpdate();
    }
  }
}
