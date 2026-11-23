package io.openaev.api.threat_arsenal;

import static io.openaev.api.catalog.CatalogRoutes.TENANT_CATALOG_URI;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.injector_contract.fields.ContractText.textField;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.service.payload.PayloadVersionService.NOT_ANOTHER_VERSION;
import static io.openaev.service.payload.PayloadVersionService.VERSION_REVOKED;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.PAYLOAD_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.manifest;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadVersion;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.releasedPayload;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.syncRequest;
import static io.openaev.utils.fixtures.InjectorContractFixture.addField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.payload.dto.PayloadRevocationInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Command;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.catalog.ContentChange;
import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.raw.RawScenarioUsageCount;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.PayloadRevocationRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.WorkflowNotEditableException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.service.chaining.StepService;
import io.openaev.service.payload.PayloadUsageMoveService;
import io.openaev.service.payload.PayloadUsageMoveService.Moves;
import io.openaev.service.payload.PayloadUsageMoveService.Refusal;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CatalogReleaseFixture.ReleasedPayload;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import io.openaev.utilstest.RowTenantScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,CATALOG")
@DisplayName("Threat Arsenal payload versions")
class ThreatArsenalVersionApiTest extends IntegrationTest {

  private static final ReleasedPayload RELEASED = releasedPayload();

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private RowTenantScope rowTenantScope;
  @Autowired private PayloadRevocationRepository payloadRevocationRepository;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepRepository stepRepository;
  @Autowired private StepService stepService;
  @Autowired private PayloadVersionService payloadVersionService;
  @Autowired private PayloadUsageMoveService payloadUsageMoveService;

  @BeforeEach
  void attachToDefaultTenant() {
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
    injectorFixture.getWellKnownOaevImplantInjector();
  }

  // -- HELPERS --

  /** Publishes these versions of the released payload and installs each: their payload ids. */
  private List<String> installed(String... versions) throws Exception {
    Map<String, byte[]> files = new LinkedHashMap<>();
    Arrays.stream(versions).forEach(v -> files.put(payloadFileName(v), RELEASED.file()));
    List<Map<String, Object>> payloads =
        Arrays.stream(versions).map(v -> payloadVersion(v, "VERIFIED", RELEASED)).toList();
    mvc.perform(
            syncRequest(
                    mapper.writeValueAsBytes(manifest("1.261109.0", List.of(), payloads)),
                    files,
                    TENANT_CATALOG_URI + "/sync",
                    DEFAULT_TENANT_UUID)
                .with(csrf()))
        .andExpect(status().isOk());
    List<String> ids = new ArrayList<>();
    for (String version : versions) {
      ResultActions install =
          mvc.perform(
                  post(
                          TENANT_CATALOG_URI + "/payloads/{externalId}/versions/{version}/install",
                          DEFAULT_TENANT_UUID,
                          PAYLOAD_ID,
                          version)
                      .with(csrf()))
              .andExpect(status().isCreated());
      entityManager.flush();
      ids.add(body(install).path("installed_id").asText());
    }
    return ids;
  }

  private Payload payload(String payloadId) {
    return payloadRepository.findById(payloadId).orElseThrow();
  }

  private InjectorContract contractOf(String payloadId) {
    return injectorContractRepository
        .findInjectorContractByPayload(payload(payloadId))
        .orElseThrow();
  }

  /** An atomic testing has no parent to take a tenant from: it carries its own, as on creation. */
  private void atomicTestingOn(InjectorContract contract) {
    Inject atomicTesting = InjectFixture.createInject(contract, "Atomic testing");
    atomicTesting.setTenant(new Tenant(DEFAULT_TENANT_UUID));
    rowTenantScope.write(DEFAULT_TENANT_UUID, () -> injectRepository.save(atomicTesting));
  }

  /** A scenario with an inject on the contract, and a chained step on it in its logic map. */
  private String scenarioRunning(InjectorContract contract) {
    Step step = StepFixture.getDefaultStepTemplate();
    step.setData(
        "{\"inject_title\": \"Chained step\", \"inject_injector_contract\": {\"injector_contract_id\": \""
            + contract.getId()
            + "\"}}");
    ScenarioComposer.Composer scenario =
        scenarioComposer
            .forScenario(ScenarioFixture.getScenario())
            .withInject(
                injectComposer.forInject(InjectFixture.createInject(contract, "Scenario inject")));
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withScenario(scenario)
        .withStep(stepComposer.forStep(step))
        .persist();
    entityManager.flush();
    return scenario.get().getId();
  }

  /** A local payload action, built without the catalogue routes a non-admin cannot call. */
  private InjectorContract localAction() {
    InjectorContract action =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))
            .persist()
            .get();
    entityManager.flush();
    return action;
  }

  /** Two local versions of one payload: the first is 1.0.0, the second 2.0.0. */
  private List<InjectorContract> localVersions() {
    InjectorContract first = localAction();
    InjectorContract second = localAction();
    second.getPayload().setIdentity(first.getPayload().getIdentity());
    second.getPayload().setVersion("2.0.0");
    payloadRepository.save(second.getPayload());
    entityManager.flush();
    return List.of(first, second);
  }

  private List<String> usageIds(InjectorContract action) {
    return payloadVersionService.usages(DEFAULT_TENANT_UUID, action.getId()).usages().stream()
        .map(usage -> usage.id())
        .toList();
  }

  /** After a request, which clears the security context the services read the user from. */
  private List<String> listedUsageIds(InjectorContract action) throws Exception {
    JsonNode usages =
        body(
            mvc.perform(
                    get(
                        TENANT_THREAT_ARSENAL_URL + "/{actionId}/usages",
                        DEFAULT_TENANT_UUID,
                        action.getId()))
                .andExpect(status().isOk()));
    return usages.path("payload_usages").findValuesAsText("usage_id");
  }

  private ResultActions moveRequest(
      InjectorContract source, String targetPayloadId, List<String> usageIds) throws Exception {
    return mvc.perform(
        post(
                TENANT_THREAT_ARSENAL_URL + "/{actionId}/usages/move",
                DEFAULT_TENANT_UUID,
                source.getId())
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                mapper.writeValueAsString(
                    Map.of("target_payload_id", targetPayloadId, "usage_ids", usageIds))));
  }

  private Moves move(InjectorContract source, InjectorContract target, List<String> usageIds) {
    return payloadUsageMoveService.move(
        TxCtx.forTenant(DEFAULT_TENANT_UUID),
        DEFAULT_TENANT_UUID,
        source.getId(),
        target.getPayload().getId(),
        usageIds);
  }

  private String contractIdOf(Inject inject) {
    return injectRepository
        .findById(inject.getId())
        .orElseThrow()
        .getInjectorContract()
        .orElseThrow()
        .getId();
  }

  private JsonNode body(ResultActions result) throws Exception {
    return mapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  // -- VERSIONS --

  @Test
  @DisplayName("lists the installed versions newest first, with their revocations")
  void given_installedVersions_should_listThemNewestFirst() throws Exception {
    // Arrange
    List<String> ids = installed("1.0.0", "1.10.0", "1.9.0");
    String first = ids.get(0);
    String latest = ids.get(1);
    String revokedByAdmin = ids.get(2);
    payloadRevocationRepository.save(PayloadRevocation.byCatalogue(payload(first)));
    payloadRevocationRepository.save(
        PayloadRevocation.byAdmin(payload(revokedByAdmin), "Too noisy", null));
    entityManager.flush();

    // Act
    JsonNode versions =
        body(
            mvc.perform(
                    get(
                        TENANT_THREAT_ARSENAL_URL + "/{actionId}/versions",
                        DEFAULT_TENANT_UUID,
                        contractOf(first).getId()))
                .andExpect(status().isOk()));

    // Assert
    assertThat(versions.findValuesAsText("payload_id"))
        .containsExactly(latest, revokedByAdmin, first);
    assertThat(versions.findValuesAsText("payload_action_id"))
        .containsExactly(
            contractOf(latest).getId(),
            contractOf(revokedByAdmin).getId(),
            contractOf(first).getId());
    assertThat(versions.findValuesAsText("payload_version"))
        .containsExactly("1.10.0", "1.9.0", "1.0.0");
    assertThat(versions.findValuesAsText("payload_is_latest"))
        .containsExactly("true", "false", "false");
    assertThat(versions.findValuesAsText("payload_revoked"))
        .containsExactly("false", "true", "true");
    assertThat(versions.findValuesAsText("payload_revoked_by_catalogue"))
        .containsExactly("false", "false", "true");
    assertThat(versions.get(1).path("payload_revocation_reason").asText()).isEqualTo("Too noisy");
    assertThat(versions.get(2).path("payload_revocation_reason").asText())
        .isEqualTo(PayloadRevocation.REVOKED_BY_FILIGRAN);
    assertThat(versions.get(0).path("payload_revoked_at").isNull()).isTrue();
  }

  // -- USAGES --

  @Test
  @DisplayName("lists the scenario injects, atomic testings and chained steps running a version")
  void given_versionInUse_should_listWhereItRuns() throws Exception {
    // Arrange
    List<String> ids = installed("1.0.0", "1.1.0");
    InjectorContract used = contractOf(ids.get(0));
    atomicTestingOn(used);
    String scenarioId = scenarioRunning(used);
    atomicTestingOn(contractOf(ids.get(1)));
    entityManager.flush();

    // Act & Assert
    mvc.perform(
            get(
                TENANT_THREAT_ARSENAL_URL + "/{actionId}/usages",
                DEFAULT_TENANT_UUID,
                used.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload_usages.length()").value(3))
        .andExpect(jsonPath("$.payload_usages_hidden_count").value(0))
        .andExpect(
            jsonPath("$.payload_usages[*].usage_type")
                .value(containsInAnyOrder("inject", "atomic_testing", "step")))
        .andExpect(
            jsonPath("$.payload_usages[*].usage_name")
                .value(containsInAnyOrder("Scenario inject", "Atomic testing", "Chained step")))
        .andExpect(
            jsonPath("$.payload_usages[?(@.usage_type == 'step')].usage_scenario_id")
                .value(scenarioId))
        .andExpect(
            jsonPath("$.payload_usages[?(@.usage_type == 'inject')].usage_scenario_id")
                .value(scenarioId));
  }

  @Test
  @WithMockUser(withCapabilities = {Capability.ACCESS_THREAT_ARSENALS})
  @DisplayName("counts the usages in a scenario the user cannot read without naming them")
  void given_scenarioNotReadable_should_countItsUsagesOnly() throws Exception {
    // Arrange
    InjectorContract action = localAction();
    scenarioRunning(action);

    // Act & Assert
    mvc.perform(
            get(
                TENANT_THREAT_ARSENAL_URL + "/{actionId}/usages",
                DEFAULT_TENANT_UUID,
                action.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload_usages.length()").value(0))
        .andExpect(jsonPath("$.payload_usages_hidden_count").value(2));
  }

  @Test
  @WithMockUser(withCapabilities = {Capability.ACCESS_THREAT_ARSENALS})
  @DisplayName("lists the usages in a scenario the user is granted to read")
  void given_scenarioGranted_should_listItsUsages() throws Exception {
    // Arrange
    InjectorContract action = localAction();
    String scenarioId = scenarioRunning(action);
    addGrantToCurrentUser(
        Grant.GRANT_RESOURCE_TYPE.SCENARIO, Grant.GRANT_TYPE.OBSERVER, scenarioId);

    // Act & Assert
    mvc.perform(
            get(
                TENANT_THREAT_ARSENAL_URL + "/{actionId}/usages",
                DEFAULT_TENANT_UUID,
                action.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload_usages.length()").value(2))
        .andExpect(jsonPath("$.payload_usages_hidden_count").value(0));
  }

  // -- USAGE COUNTS --

  @Nested
  @DisplayName("Usage counts of each version")
  class UsageCounts {

    private void simulationInjectOn(InjectorContract contract) {
      injectComposer
          .forInject(InjectFixture.createInject(contract, "Simulation inject"))
          .withExercise(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
          .persist();
      entityManager.flush();
    }

    /** An atomic testing of another tenant, on its own copy of the action under the same id. */
    private void atomicTestingOfAnotherTenantOn(InjectorContract contract) throws Exception {
      String otherTenantId = tenantHelper.createTenantWithCurrentUser("usage-counts-other").getId();
      Inject atomicTesting = InjectFixture.createInject(contract, "Other tenant atomic testing");
      atomicTesting.setTenant(new Tenant(otherTenantId));
      rowTenantScope.write(
          otherTenantId,
          () -> {
            entityManager
                .createNativeQuery(
                    "INSERT INTO injectors_contracts (injector_contract_id, tenant_id,"
                        + " injector_contract_content, injector_contract_import_available)"
                        + " VALUES (:id, :tenant, '{}', false)")
                .setParameter("id", contract.getId())
                .setParameter("tenant", otherTenantId)
                .executeUpdate();
            injectRepository.save(atomicTesting);
          });
      entityManager.flush();
    }

    private Map<String, Long> counts(InjectorContract action) throws Exception {
      JsonNode counts =
          body(
              mvc.perform(
                      get(
                          TENANT_THREAT_ARSENAL_URL + "/{actionId}/usage-counts",
                          DEFAULT_TENANT_UUID,
                          action.getId()))
                  .andExpect(status().isOk()));
      Map<String, Long> byVersion = new LinkedHashMap<>();
      counts.forEach(
          count ->
              byVersion.put(
                  count.path("payload_id").asText(), count.path("payload_usage_count").asLong()));
      return byVersion;
    }

    @Test
    @DisplayName(
        "counts the injects, atomic testings and chained steps of each version in one call")
    void given_versionsInUse_should_countTheUsagesOfEach() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      InjectorContract older = versions.get(0);
      InjectorContract latest = versions.get(1);
      atomicTestingOn(older);
      scenarioRunning(older);
      atomicTestingOn(latest);
      entityManager.flush();

      // Act
      Map<String, Long> counts = counts(latest);

      // Assert
      assertThat(counts)
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(older.getPayload().getId(), 3L, latest.getPayload().getId(), 1L));
    }

    @Test
    @DisplayName("counts neither simulation injects nor the injects of another tenant")
    void given_simulationAndOtherTenantInjects_should_notCountThem() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      InjectorContract older = versions.get(0);
      atomicTestingOn(older);
      simulationInjectOn(older);
      simulationInjectOn(versions.get(1));
      atomicTestingOfAnotherTenantOn(older);

      // Act
      Map<String, Long> counts = counts(versions.get(1));

      // Assert
      assertThat(counts).containsExactlyEntriesOf(Map.of(older.getPayload().getId(), 1L));
    }

    @Test
    @DisplayName("counts the scenario injects and chained steps of each scenario, nothing else")
    void given_versionsInUse_should_countTheirUsagesByScenario() {
      // Arrange
      List<InjectorContract> versions = localVersions();
      InjectorContract older = versions.get(0);
      String scenarioId = scenarioRunning(older);
      atomicTestingOn(older);
      simulationInjectOn(older);
      scenarioRunning(versions.get(1));

      // Act
      List<RawScenarioUsageCount> scenarios =
          payloadVersionService.scenarioUsages(
              DEFAULT_TENANT_UUID, List.of(payload(older.getPayload().getId())));

      // Assert
      assertThat(scenarios).hasSize(1);
      assertThat(scenarios.getFirst().getScenarioId()).isEqualTo(scenarioId);
      assertThat(scenarios.getFirst().getScenarioName())
          .isEqualTo(ScenarioFixture.getScenario().getName());
      assertThat(scenarios.getFirst().getUsageCount()).isEqualTo(2L);
    }
  }

  // -- OTHER VERSION --

  @Nested
  @DisplayName("The action of another version of a payload")
  class OtherVersion {

    @Test
    @DisplayName("returns the action of another installed version")
    void given_otherInstalledVersion_should_returnItsAction() throws Exception {
      // Arrange
      List<String> ids = installed("1.0.0", "1.1.0");
      Payload current = payload(ids.get(0));
      String other = contractOf(ids.get(1)).getId();

      // Act & Assert
      assertThat(
              payloadVersionService
                  .otherVersionAction(DEFAULT_TENANT_UUID, current, ids.get(1))
                  .getId())
          .isEqualTo(other);
      assertThat(
              payloadVersionService
                  .otherVersionActionById(DEFAULT_TENANT_UUID, current, other)
                  .getId())
          .isEqualTo(other);
    }

    @Test
    @DisplayName("refuses the same version, another payload and a revoked version")
    void given_notAnotherUnrevokedVersion_should_refuseIt() throws Exception {
      // Arrange
      List<String> ids = installed("1.0.0", "1.1.0");
      Payload current = payload(ids.get(0));
      String otherPayload = localAction().getPayload().getId();
      payloadRevocationRepository.save(
          PayloadRevocation.byAdmin(payload(ids.get(1)), "Too noisy", null));
      entityManager.flush();

      // Act & Assert
      assertThatThrownBy(
              () ->
                  payloadVersionService.otherVersionAction(
                      DEFAULT_TENANT_UUID, current, ids.get(0)))
          .isInstanceOf(BadRequestException.class)
          .hasMessage(NOT_ANOTHER_VERSION);
      assertThatThrownBy(
              () ->
                  payloadVersionService.otherVersionAction(
                      DEFAULT_TENANT_UUID, current, otherPayload))
          .isInstanceOf(BadRequestException.class)
          .hasMessage(NOT_ANOTHER_VERSION);
      assertThatThrownBy(
              () ->
                  payloadVersionService.otherVersionAction(
                      DEFAULT_TENANT_UUID, current, ids.get(1)))
          .isInstanceOf(BadRequestException.class)
          .hasMessage(VERSION_REVOKED);
    }
  }

  // -- LISTED ACTION --

  @Test
  @DisplayName("opens an older payload version on the action the Threat Arsenal lists")
  void given_olderVersion_should_returnTheActionOfTheLatest() throws Exception {
    // Arrange
    List<String> ids = installed("1.0.0", "1.1.0");
    String local = localAction().getPayload().getId();

    // Act & Assert
    for (Map.Entry<String, String> link :
        Map.of(ids.get(0), ids.get(1), ids.get(1), ids.get(1), local, local).entrySet()) {
      JsonNode action =
          body(
              mvc.perform(
                      get(
                          TENANT_THREAT_ARSENAL_URL + "/payloads/{payloadId}/action",
                          DEFAULT_TENANT_UUID,
                          link.getKey()))
                  .andExpect(status().isOk()));
      assertThat(action.path("action_id").asText()).isEqualTo(contractOf(link.getValue()).getId());
    }
  }

  @Test
  @DisplayName("does not open the action of a payload of another tenant")
  void given_payloadOfAnotherTenant_should_notFindItsAction() throws Exception {
    // Arrange
    String payloadId = localAction().getPayload().getId();
    String otherTenantId = tenantHelper.createTenantWithCurrentUser("listed-action-other").getId();

    // Act & Assert
    mvc.perform(
            get(
                TENANT_THREAT_ARSENAL_URL + "/payloads/{payloadId}/action",
                otherTenantId,
                payloadId))
        .andExpect(status().isNotFound());
  }

  // -- CHANGES --

  @Test
  @DisplayName("says what a version changed since the version just below it")
  void given_newerVersion_should_listItsChangedFields() throws Exception {
    // Arrange
    List<InjectorContract> versions = localVersions();
    Command newer = (Command) versions.get(1).getPayload();
    newer.setContent("net view");
    payloadRepository.save(newer);
    entityManager.flush();

    // Act
    JsonNode changes =
        body(
            mvc.perform(
                    get(
                        TENANT_THREAT_ARSENAL_URL + "/{actionId}/changes",
                        DEFAULT_TENANT_UUID,
                        versions.get(1).getId()))
                .andExpect(status().isOk()));

    // Assert
    assertThat(changes.findValuesAsText("change_field")).containsExactly("command_content");
    assertThat(changes.findValuesAsText("change_after")).containsExactly("net view");
  }

  @Test
  @DisplayName("has no change for the first version")
  void given_firstVersion_should_haveNoChange() throws Exception {
    // Arrange
    List<InjectorContract> versions = localVersions();

    // Act & Assert
    mvc.perform(
            get(
                TENANT_THREAT_ARSENAL_URL + "/{actionId}/changes",
                DEFAULT_TENANT_UUID,
                versions.get(0).getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // -- HISTORY --

  private JsonNode history(String actionId, int size) throws Exception {
    SearchPaginationInput input = new SearchPaginationInput();
    input.setSize(size);
    return body(
        mvc.perform(
                post(
                        TENANT_THREAT_ARSENAL_URL + "/{actionId}/history/search",
                        DEFAULT_TENANT_UUID,
                        actionId)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isOk()));
  }

  @Test
  @DisplayName("lists who installed each version of the payload, newest first")
  void given_installedVersions_should_listTheirInstallsNewestFirst() throws Exception {
    // Arrange
    List<String> ids = installed("1.0.0", "1.1.0");

    // Act
    JsonNode history = history(contractOf(ids.getFirst()).getId(), 20).get("content");

    // Assert
    assertThat(history.findValuesAsText("content_change_version"))
        .containsExactly("1.1.0", "1.0.0");
    assertThat(history.findValuesAsText("content_change_action"))
        .containsOnly(ContentChange.Action.INSTALL.name());
    assertThat(history.findValuesAsText("content_change_mode"))
        .containsOnly(ContentChange.Mode.MANUAL.name());
    assertThat(history.findValuesAsText("content_change_user")).doesNotContain("", "null");
    assertThat(history.findValues("content_change_fields")).allMatch(JsonNode::isNull);
  }

  @Test
  @DisplayName("pages the history of the payload")
  void given_historyLongerThanAPage_should_returnOnePage() throws Exception {
    // Arrange
    List<String> ids = installed("1.0.0", "1.1.0");

    // Act
    JsonNode page = history(contractOf(ids.getFirst()).getId(), 1);

    // Assert
    assertThat(page.get("content")).hasSize(1);
    assertThat(page.get("totalElements").asInt()).isEqualTo(2);
    assertThat(page.get("content").get(0).get("content_change_version").asText())
        .isEqualTo("1.1.0");
  }

  @Test
  @DisplayName("lists the save that added a version to a payload made in the tenant")
  void given_localPayloadSavedAsNewVersion_should_listTheSave() throws Exception {
    // Arrange
    InjectorContract action = localAction();
    PayloadUpdateInput input = PayloadInputFixture.getDefaultCommandPayloadUpdateInput(List.of());
    input.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);
    mvc.perform(
            put(
                    TENANT_PAYLOAD_URI + "/{payloadId}",
                    DEFAULT_TENANT_UUID,
                    action.getPayload().getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)))
        .andExpect(status().isOk());

    // Act
    JsonNode history = history(action.getId(), 20).get("content");

    // Assert
    assertThat(history.findValuesAsText("content_change_action"))
        .containsExactly(ContentChange.Action.SAVE.name());
    assertThat(history.findValuesAsText("content_change_mode"))
        .containsExactly(ContentChange.Mode.MANUAL.name());
    assertThat(history.findValuesAsText("content_change_version"))
        .doesNotContain(Payload.FIRST_VERSION);
    assertThat(history.get(0).get("content_change_from_version").asText())
        .isEqualTo(Payload.FIRST_VERSION);
    assertThat(history.get(0).get("content_change_fields").findValuesAsText("change_field"))
        .isNotEmpty();
  }

  @Test
  @DisplayName("lists the revocation and its lift on a payload made in the tenant")
  void given_localPayloadRevokedThenLifted_should_listBoth() throws Exception {
    // Arrange
    InjectorContract action = localAction();
    String payloadId = action.getPayload().getId();
    mvc.perform(
            post(TENANT_PAYLOAD_URI + "/{payloadId}/revocation", DEFAULT_TENANT_UUID, payloadId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new PayloadRevocationInput("Too noisy"))))
        .andExpect(status().isCreated());
    mvc.perform(
            delete(TENANT_PAYLOAD_URI + "/{payloadId}/revocation", DEFAULT_TENANT_UUID, payloadId)
                .with(csrf()))
        .andExpect(status().isNoContent());

    // Act
    JsonNode history = history(action.getId(), 20).get("content");

    // Assert
    assertThat(history.findValuesAsText("content_change_action"))
        .containsExactlyInAnyOrder(
            ContentChange.Action.REVOKE.name(), ContentChange.Action.LIFT_REVOCATION.name());
  }

  // -- SEARCH --

  @Test
  @DisplayName("shows the version, the catalogue origin and the revocation on the search cards")
  void given_revokedCatalogueVersion_should_showItOnTheSearchCard() throws Exception {
    // Arrange
    String payloadId = installed("1.0.0").getFirst();
    payloadRevocationRepository.save(
        PayloadRevocation.byAdmin(payload(payloadId), "Too noisy", null));
    entityManager.flush();
    SearchPaginationInput input = new SearchPaginationInput();
    input.setTextSearch("Crowdoor");

    // Act & Assert
    mvc.perform(
            post(TENANT_THREAT_ARSENAL_URL + "/search", DEFAULT_TENANT_UUID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].action_payload.payload_id").value(payloadId))
        .andExpect(jsonPath("$.content[0].action_payload_version").value("1.0.0"))
        .andExpect(jsonPath("$.content[0].action_payload_managed").value(true))
        .andExpect(jsonPath("$.content[0].action_payload_revoked").value(true));
  }

  // -- MOVE --

  @Nested
  @DisplayName("Moving usages to another version")
  class MoveUsages {

    @Test
    @DisplayName(
        "moves scenario injects, atomic testings and chained steps, never simulation injects")
    void given_usagesOnV1_should_moveThemToV2() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      InjectorContract v1 = versions.get(0);
      InjectorContract v2 = versions.get(1);
      scenarioRunning(v1);
      Inject atomicTesting =
          injectComposer
              .forInject(InjectFixture.createInject(v1, "Atomic testing"))
              .persist()
              .get();
      Inject simulationInject =
          injectComposer
              .forInject(InjectFixture.createInject(v1, "Simulation inject"))
              .withExercise(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
              .persist()
              .get();
      entityManager.flush();
      List<String> usageIds = new ArrayList<>(usageIds(v1));
      usageIds.add(simulationInject.getId());

      // Act
      JsonNode moves =
          body(moveRequest(v1, v2.getPayload().getId(), usageIds).andExpect(status().isOk()));

      // Assert
      assertThat(moves.path("moved_ids").size()).isEqualTo(3);
      assertThat(moves.path("refused").findValuesAsText("usage_id"))
          .containsExactly(simulationInject.getId());
      assertThat(moves.path("refused").findValuesAsText("reason"))
          .containsExactly(Refusal.NOT_ON_THIS_VERSION.name());
      entityManager.flush();
      entityManager.clear();
      assertThat(contractIdOf(atomicTesting)).isEqualTo(v2.getId());
      assertThat(contractIdOf(simulationInject)).isEqualTo(v1.getId());
      assertThat(listedUsageIds(v1)).isEmpty();
      List<String> onV2 = listedUsageIds(v2);
      assertThat(onV2).hasSize(3);
      Step step = stepRepository.findAllById(onV2).getFirst();
      JsonNode stepContract = mapper.readTree(step.getData()).path("inject_injector_contract");
      assertThat(stepContract.path("injector_contract_id").asText()).isEqualTo(v2.getId());
      assertThat(stepContract.path("injector_contract_payload").isMissingNode()).isFalse();
    }

    @Test
    @DisplayName("refuses a revoked target version")
    void given_revokedTarget_should_refuseTheMove() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      atomicTestingOn(versions.get(0));
      payloadRevocationRepository.save(
          PayloadRevocation.byAdmin(versions.get(1).getPayload(), "Too noisy", null));
      entityManager.flush();

      // Act & Assert
      moveRequest(versions.get(0), versions.get(1).getPayload().getId(), usageIds(versions.get(0)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuses a target that is not a version of the same payload")
    void given_otherPayload_should_refuseTheMove() throws Exception {
      // Arrange
      InjectorContract action = localAction();
      InjectorContract other = localAction();
      atomicTestingOn(action);
      entityManager.flush();

      // Act & Assert
      moveRequest(action, other.getPayload().getId(), usageIds(action))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("Usages that stay on their version")
  class MoveRefusals {

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_THREAT_ARSENALS})
    @DisplayName("keeps the usages of a scenario the user cannot update")
    void given_readOnlyScenario_should_keepItsUsages() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      String scenarioId = scenarioRunning(versions.get(0));
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SCENARIO, Grant.GRANT_TYPE.OBSERVER, scenarioId);
      List<String> usageIds = usageIds(versions.get(0));
      assertThat(usageIds).hasSize(2);

      // Act
      Moves moves = move(versions.get(0), versions.get(1), usageIds);

      // Assert
      assertThat(moves.moved()).isEmpty();
      assertThat(moves.refused())
          .containsOnlyKeys(usageIds)
          .allSatisfy((usageId, refusal) -> assertThat(refusal).isEqualTo(Refusal.MISSING_RIGHT));
    }

    @Test
    @DisplayName("keeps the usages that leave empty a field the target version needs")
    void given_targetNeedsAnEmptyField_should_keepTheUsages() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      InjectorContract target = versions.get(1);
      addField(target, mapper, List.of(textField("new_argument", "New argument")));
      injectorContractRepository.save(target);
      scenarioRunning(versions.get(0));
      entityManager.clear();
      List<String> usageIds = usageIds(versions.get(0));
      assertThat(usageIds).hasSize(2);

      // Act
      Moves moves = move(versions.get(0), target, usageIds);

      // Assert
      assertThat(moves.moved()).isEmpty();
      assertThat(moves.refused())
          .containsOnlyKeys(usageIds)
          .allSatisfy((usageId, refusal) -> assertThat(refusal).isEqualTo(Refusal.NOT_COMPATIBLE));
    }

    @Test
    @DisplayName("keeps a chained step of a launched simulation")
    void given_stepOfLaunchedSimulation_should_refuseToUpdateIt() {
      // Arrange
      List<InjectorContract> versions = localVersions();
      Step step = StepFixture.getDefaultStepTemplate();
      workflowComposer
          .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
          .withSimulation(
              exerciseComposer.forExercise(ExerciseFixture.createRunningAttackExercise()))
          .withStep(stepComposer.forStep(step))
          .persist();
      entityManager.flush();

      // Act & Assert
      assertThatThrownBy(
              () -> stepService.updateStepTemplateInjectorContract(step, versions.get(1)))
          .isInstanceOf(WorkflowNotEditableException.class);
    }
  }

  // -- INJECT VERSION --

  @Nested
  @DisplayName("Choosing the version an inject runs")
  class InjectVersion {

    private Inject scenarioInjectOn(InjectorContract contract) {
      InjectComposer.Composer inject =
          injectComposer.forInject(InjectFixture.createInject(contract, "Scenario inject"));
      scenarioComposer.forScenario(ScenarioFixture.getScenario()).withInject(inject).persist();
      entityManager.flush();
      return inject.get();
    }

    private ResultActions chooseInScenario(Inject inject, InjectorContract chosen)
        throws Exception {
      return mvc.perform(
          put(
                  TENANT_SCENARIO_URI + "/{scenarioId}/injects/{injectId}",
                  DEFAULT_TENANT_UUID,
                  inject.getScenario().getId(),
                  inject.getId())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(mapper.writeValueAsString(input(inject, chosen))));
    }

    private InjectInput input(Inject inject, InjectorContract chosen) {
      InjectInput input = new InjectInput();
      input.setTitle(inject.getTitle());
      input.setInjectorContract(chosen.getId());
      input.setDependsDuration(inject.getDependsDuration());
      return input;
    }

    @Test
    @DisplayName("moves a scenario inject to another installed version of its payload")
    void given_otherInstalledVersion_should_runIt() throws Exception {
      // Arrange
      List<String> ids = installed("1.0.0", "1.1.0");
      Inject inject = scenarioInjectOn(contractOf(ids.get(0)));
      String chosen = contractOf(ids.get(1)).getId();

      // Act & Assert
      chooseInScenario(inject, contractOf(ids.get(1)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.inject_injector_contract.injector_contract_id").value(chosen));
    }

    @Test
    @DisplayName("refuses a revoked version")
    void given_revokedVersion_should_refuseIt() throws Exception {
      // Arrange
      List<String> ids = installed("1.0.0", "1.1.0");
      Inject inject = scenarioInjectOn(contractOf(ids.get(0)));
      InjectorContract revoked = contractOf(ids.get(1));
      payloadRevocationRepository.save(
          PayloadRevocation.byAdmin(payload(ids.get(1)), "Too noisy", null));
      entityManager.flush();
      entityManager.clear();

      // Act & Assert
      chooseInScenario(inject, revoked).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuses the action of another payload")
    void given_otherPayload_should_refuseIt() throws Exception {
      // Arrange
      Inject inject = scenarioInjectOn(contractOf(installed("1.0.0").getFirst()));

      // Act & Assert
      chooseInScenario(inject, localAction()).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("keeps the version of a simulation inject")
    void given_simulationInject_should_keepItsVersion() throws Exception {
      // Arrange
      List<String> ids = installed("1.0.0", "1.1.0");
      InjectorContract current = contractOf(ids.get(0));
      InjectComposer.Composer inject =
          injectComposer.forInject(InjectFixture.createInject(current, "Simulation inject"));
      exerciseComposer
          .forExercise(ExerciseFixture.createDefaultExercise())
          .withInject(inject)
          .persist();
      entityManager.flush();

      // Act & Assert
      mvc.perform(
              put(
                      TENANT_PREFIX + "/injects/{exerciseId}/{injectId}",
                      DEFAULT_TENANT_UUID,
                      inject.get().getExercise().getId(),
                      inject.get().getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(input(inject.get(), contractOf(ids.get(1))))))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.inject_injector_contract.injector_contract_id").value(current.getId()));
    }
  }

  // -- OLDER VERSIONS IN USE --

  @Nested
  @DisplayName("Cards of actions with injects on an older version")
  class OlderVersionsInUse {

    private static final String OLDER_VERSIONS_IN_USE_URL =
        TENANT_THREAT_ARSENAL_URL + "/older-versions-in-use";
    private static final String OLDER_VERSIONS_KEPT_URL =
        TENANT_THREAT_ARSENAL_URL + "/{actionId}/older-versions-kept";

    private List<String> flagged(String tenantId, InjectorContract... actions) throws Exception {
      List<String> ids = new ArrayList<>();
      olderUsages(tenantId, actions).fieldNames().forEachRemaining(ids::add);
      return ids;
    }

    private JsonNode olderUsages(String tenantId, InjectorContract... actions) throws Exception {
      List<String> actionIds = Arrays.stream(actions).map(InjectorContract::getId).toList();
      return body(
          mvc.perform(
                  post(OLDER_VERSIONS_IN_USE_URL, tenantId)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(Map.of("action_ids", actionIds))))
              .andExpect(status().isOk()));
    }

    private ResultActions keep(String tenantId, InjectorContract action) throws Exception {
      return mvc.perform(put(OLDER_VERSIONS_KEPT_URL, tenantId, action.getId()).with(csrf()));
    }

    /** A chained step of a scenario logic map, its contract written as a plain id. */
    private void chainedStepOn(InjectorContract contract) {
      Step step = StepFixture.getDefaultStepTemplate();
      step.setData("{\"inject_injector_contract\": \"" + contract.getId() + "\"}");
      workflowComposer
          .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
          .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
          .withStep(stepComposer.forStep(step))
          .persist();
      entityManager.flush();
    }

    private InjectorContract newerVersion(InjectorContract of, String version) {
      InjectorContract newer = localAction();
      newer.getPayload().setIdentity(of.getPayload().getIdentity());
      newer.getPayload().setVersion(version);
      payloadRepository.save(newer.getPayload());
      entityManager.flush();
      return newer;
    }

    @Test
    @DisplayName("flags the latest version when an atomic testing still runs an older one")
    void given_atomicTestingOnOlderVersion_should_flagTheLatestOnly() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      atomicTestingOn(versions.get(0));
      entityManager.flush();

      // Act & Assert
      assertThat(flagged(DEFAULT_TENANT_UUID, versions.get(0), versions.get(1)))
          .containsExactly(versions.get(1).getId());
    }

    @Test
    @DisplayName("flags the latest version when a chained scenario step still runs an older one")
    void given_chainedStepOnOlderVersion_should_flagTheLatest() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      chainedStepOn(versions.get(0));

      // Act & Assert
      assertThat(flagged(DEFAULT_TENANT_UUID, versions.get(1)))
          .containsExactly(versions.get(1).getId());
    }

    @Test
    @DisplayName("counts the atomic testings and chained steps still on older versions")
    void given_usagesOnOlderVersions_should_countThem() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      atomicTestingOn(versions.get(0));
      atomicTestingOn(versions.get(0));
      chainedStepOn(versions.get(0));

      // Act
      JsonNode usages = olderUsages(DEFAULT_TENANT_UUID, versions.get(1));

      // Assert
      assertThat(usages.path(versions.get(1).getId()).asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("does not flag a version whose older ones only run in simulations")
    void given_simulationInjectOnOlderVersion_should_notFlag() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      injectComposer
          .forInject(InjectFixture.createInject(versions.get(0), "Simulation inject"))
          .withExercise(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
          .persist();
      entityManager.flush();

      // Act & Assert
      assertThat(flagged(DEFAULT_TENANT_UUID, versions.get(1))).isEmpty();
    }

    @Test
    @DisplayName("hides the flag once kept on their version, until a newer version arrives")
    void given_keptOnTheirVersion_should_hideTheFlagUntilANewerVersion() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      InjectorContract latest = versions.get(1);
      atomicTestingOn(versions.get(0));
      entityManager.flush();

      // Act
      keep(DEFAULT_TENANT_UUID, latest).andExpect(status().isOk());
      entityManager.flush();

      // Assert
      assertThat(flagged(DEFAULT_TENANT_UUID, latest)).isEmpty();
      JsonNode shown =
          body(
              mvc.perform(
                      get(
                          TENANT_THREAT_ARSENAL_URL + "/{actionId}/versions",
                          DEFAULT_TENANT_UUID,
                          latest.getId()))
                  .andExpect(status().isOk()));
      assertThat(shown.findValuesAsText("payload_older_versions_kept"))
          .containsExactly("true", "false");
      InjectorContract newer = newerVersion(latest, "3.0.0");
      assertThat(flagged(DEFAULT_TENANT_UUID, newer)).containsExactly(newer.getId());
    }

    @Test
    @DisplayName("flags the version again once the banner is shown again")
    void given_shownAgain_should_flagAgain() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      InjectorContract latest = versions.get(1);
      atomicTestingOn(versions.get(0));
      entityManager.flush();
      keep(DEFAULT_TENANT_UUID, latest).andExpect(status().isOk());

      // Act
      mvc.perform(delete(OLDER_VERSIONS_KEPT_URL, DEFAULT_TENANT_UUID, latest.getId()).with(csrf()))
          .andExpect(status().isNoContent());
      entityManager.flush();

      // Assert
      assertThat(flagged(DEFAULT_TENANT_UUID, latest)).containsExactly(latest.getId());
    }

    @Test
    @DisplayName("neither flags nor keeps the action of another tenant")
    void given_otherTenantPath_should_neitherFlagNorKeep() throws Exception {
      // Arrange
      List<InjectorContract> versions = localVersions();
      InjectorContract latest = versions.get(1);
      atomicTestingOn(versions.get(0));
      entityManager.flush();
      String otherTenant = tenantHelper.createTenantWithCurrentUser("older-versions-other").getId();

      // Act & Assert
      assertThat(flagged(otherTenant, latest)).isEmpty();
      keep(otherTenant, latest).andExpect(status().is4xxClientError());
      entityManager.clear();
      // The request left the other tenant's scope on the test transaction; the action belongs to
      // the default tenant, so it is read back from there.
      DefaultTenantScopeTestListener.setAmbientScope(entityManager, DEFAULT_TENANT_UUID);
      PayloadIdentity identity = payload(latest.getPayload().getId()).getIdentity();
      assertThat(identity.getOlderVersionsKeptFor()).isNull();
    }
  }
}
