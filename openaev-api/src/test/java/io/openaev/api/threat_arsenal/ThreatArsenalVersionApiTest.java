package io.openaev.api.threat_arsenal;

import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.injector_contract.fields.ContractText.textField;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Step;
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.PayloadRevocationRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.WorkflowNotEditableException;
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

  private void atomicTestingOn(InjectorContract contract) {
    injectRepository.save(InjectFixture.createInject(contract, "Atomic testing"));
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
      assertThat(usageIds(v1)).isEmpty();
      List<String> onV2 = usageIds(v2);
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
    void given_readOnlyScenario_should_keepItsUsages() {
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
}
