package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Threat Arsenal payload versioning: the approved version keeps running (Task 5)")
class ThreatArsenalPayloadVersionApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private DomainComposer domainComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private EntityManager entityManager;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectService injectService;
  @Autowired private PayloadRepository payloadRepository;

  private Authentication author;
  private Authentication approver;

  @BeforeEach
  void beforeEach() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    domainComposer.reset();
    injectComposer.reset();
    scenarioComposer.reset();
    exerciseComposer.reset();
    tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    // No access to atomic testings, scenarios or simulations: counts only.
    author =
        buildAuthenticationToken(
            userWith(
                "Author", Capability.ACCESS_THREAT_ARSENALS, Capability.MANAGE_THREAT_ARSENALS));
    approver =
        buildAuthenticationToken(
            userWith(
                "Approver",
                Capability.ACCESS_THREAT_ARSENALS,
                Capability.MANAGE_THREAT_ARSENALS,
                Capability.APPROVE_THREAT_ARSENALS));
  }

  private User userWith(String firstName, Capability... capabilities) {
    User user =
        userComposer
            .forUser(
                UserFixture.getUser(firstName, "User", UUID.randomUUID() + "@unittests.invalid"))
            .withGroup(
                tenantGroupComposer
                    .forGroup(TenantGroupFixture.getGroup())
                    .withRole(
                        tenantRoleComposer.forRole(
                            TenantRoleFixture.getRole(new HashSet<>(Set.of(capabilities))))))
            .persist()
            .get();
    tenantRepository.addUserToTenant(user.getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantMembershipCacheManager.evict(user.getId(), Tenant.DEFAULT_TENANT_UUID);
    return user;
  }

  private String url(String suffix) {
    return tenantUri(TENANT_THREAT_ARSENAL_URL) + suffix;
  }

  /** Creates a command action as the approver (auto-approved) and returns its action id. */
  private String approvedAction() throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    ThreatArsenalActionCreateInput input =
        ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));
    String response =
        mvc.perform(
                post(url(""))
                    .with(authentication(approver))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.injector_contract_id");
  }

  private InjectComposer.Composer injectUsing(String actionId, String title) {
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTitle(title);
    inject.setInjectorContract(injectorContractRepository.findById(actionId).orElseThrow());
    return injectComposer.forInject(inject);
  }

  private Exercise simulation(String name, ExerciseStatus status, String actionId) {
    Exercise exercise = ExerciseFixture.createDefaultIncidentResponseExercise();
    exercise.setName(name);
    exercise.setStatus(status);
    return exerciseComposer
        .forExercise(exercise)
        .withInject(injectUsing(actionId, "Inject of " + name))
        .persist()
        .get();
  }

  private Exercise simulationWithInject(
      String name, ExerciseStatus status, Instant start, String actionId, boolean injectRan) {
    Exercise exercise = ExerciseFixture.createDefaultIncidentResponseExercise(start);
    exercise.setName(name);
    exercise.setStatus(status);
    InjectComposer.Composer inject = injectUsing(actionId, "Inject of " + name);
    if (injectRan) {
      inject.withInjectStatus(
          injectStatusComposer.forInjectStatus(InjectStatusFixture.createSuccessStatus()));
    }
    return exerciseComposer.forExercise(exercise).withInject(inject).persist().get();
  }

  private String getJson(String uri) throws Exception {
    return mvc.perform(get(uri))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private ResultActions approveAs(Authentication as, String actionId, String fingerprint)
      throws Exception {
    return mvc.perform(
        post(url("/" + actionId + "/approve"))
            .with(authentication(as))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(Map.of("approval_fingerprint", fingerprint))));
  }

  private ResultActions rejectAs(Authentication as, String actionId, String reason)
      throws Exception {
    return mvc.perform(
        post(url("/" + actionId + "/reject"))
            .with(authentication(as))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(Map.of("approval_reason", reason))));
  }

  private String pendingFingerprint(String actionId) throws Exception {
    return JsonPath.read(detail(actionId), "$.action_pending_version.version_fingerprint");
  }

  private String versions(String actionId) throws Exception {
    return getJson(url("/" + actionId + "/versions"));
  }

  /** The action used by one atomic testing, one scenario and two simulations (one finished). */
  private String usedAction() throws Exception {
    String actionId = approvedAction();
    injectUsing(actionId, "Atomic whoami").persist();
    scenarioComposer
        .forScenario(ScenarioFixture.getScenario())
        .withInject(injectUsing(actionId, "Scenario inject"))
        .persist();
    simulation("Scheduled simulation", ExerciseStatus.SCHEDULED, actionId);
    simulation("Finished simulation", ExerciseStatus.FINISHED, actionId);
    return actionId;
  }

  private ThreatArsenalActionUpdateInput update(String content, String description) {
    return update(content, description, Endpoint.PLATFORM_TYPE.Linux);
  }

  private ThreatArsenalActionUpdateInput update(
      String content, String description, Endpoint.PLATFORM_TYPE platform) {
    return new ThreatArsenalActionUpdateInput(
        "Command line payload",
        new Endpoint.PLATFORM_TYPE[] {platform},
        description,
        "bash",
        content,
        Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
        new BaseInjectExpectation.EXPECTATION_TYPE[] {},
        Collections.emptyMap(),
        null,
        null,
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        List.of());
  }

  private ResultActions updateAs(
      Authentication as, String actionId, ThreatArsenalActionUpdateInput input) throws Exception {
    return mvc.perform(
        put(url("/" + actionId))
            .with(authentication(as))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(input)));
  }

  private String detail(String actionId) throws Exception {
    return mvc.perform(get(url("/" + actionId)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String commandContent(String actionId) throws Exception {
    return JsonPath.read(detail(actionId), "$.command_content");
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  @Nested
  @DisplayName("Usage of the payload")
  class Usage {

    @Test
    @DisplayName(
        "Given a used payload, usage should count atomic testings, scenarios and simulations still to run, with names")
    void given_usedPayload_should_countAndNameWhereItIsUsed() throws Exception {
      // Arrange
      String actionId = usedAction();

      // Act
      String usage =
          mvc.perform(get(url("/" + actionId + "/usage")))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat((Integer) JsonPath.read(usage, "$.usage_atomic_testings_count")).isEqualTo(1);
      assertThat((Integer) JsonPath.read(usage, "$.usage_scenarios_count")).isEqualTo(1);
      assertThat((Integer) JsonPath.read(usage, "$.usage_simulations_count")).isEqualTo(1);
      assertThat((List<String>) JsonPath.read(usage, "$.usage_atomic_testings[*].name"))
          .containsExactly("Atomic whoami");
      assertThat((List<String>) JsonPath.read(usage, "$.usage_simulations[*].name"))
          .containsExactly("Scheduled simulation");
    }

    @Test
    @DisplayName(
        "Given the same payload, an admin and a manager without access to assessments should get the same counts; only the names differ")
    void given_samePayload_should_giveTheSameCountsWhateverTheViewer() throws Exception {
      // Arrange
      String actionId = usedAction();

      // Act
      String asAdmin = getJson(url("/" + actionId + "/usage"));
      String asManager =
          mvc.perform(get(url("/" + actionId + "/usage")).with(authentication(author)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert: counts are complete for everyone
      for (String count :
          List.of(
              "$.usage_atomic_testings_count",
              "$.usage_scenarios_count",
              "$.usage_simulations_count")) {
        assertThat((Integer) JsonPath.read(asManager, count))
            .as(count)
            .isEqualTo((Integer) JsonPath.read(asAdmin, count));
      }
      assertThat((Integer) JsonPath.read(asManager, "$.usage_atomic_testings_count")).isEqualTo(1);
      // Only the names and links are hidden from the manager
      assertThat((List<Object>) JsonPath.read(asAdmin, "$.usage_atomic_testings")).hasSize(1);
      assertThat((Object) JsonPath.read(asManager, "$.usage_atomic_testings")).isNull();
    }

    @Test
    @DisplayName(
        "Given a payload used by more items than listed, usage should give the exact count and the first 20 names")
    void given_manyUsages_should_giveExactCountAndFirst20Names() throws Exception {
      // Arrange
      String actionId = approvedAction();
      for (int index = 1; index <= 25; index++) {
        injectUsing(actionId, String.format("Atomic %02d", index)).persist();
      }

      // Act
      String usage =
          mvc.perform(get(url("/" + actionId + "/usage")))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat((Integer) JsonPath.read(usage, "$.usage_atomic_testings_count")).isEqualTo(25);
      List<String> names = JsonPath.read(usage, "$.usage_atomic_testings[*].name");
      assertThat(names).hasSize(20).startsWith("Atomic 01").endsWith("Atomic 20");
    }

    @Test
    @DisplayName(
        "Given a user without access to atomic testings, scenarios and simulations, usage should give counts only")
    void given_userWithoutAssessmentAccess_should_getCountsOnly() throws Exception {
      // Arrange
      String actionId = usedAction();

      // Act
      String usage =
          mvc.perform(get(url("/" + actionId + "/usage")).with(authentication(author)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat((Integer) JsonPath.read(usage, "$.usage_scenarios_count")).isEqualTo(1);
      assertThat((Object) JsonPath.read(usage, "$.usage_atomic_testings")).isNull();
      assertThat((Object) JsonPath.read(usage, "$.usage_scenarios")).isNull();
      assertThat((Object) JsonPath.read(usage, "$.usage_simulations")).isNull();
    }
  }

  @Nested
  @DisplayName("An edit of an approved action becomes a pending version (US5.1)")
  class PendingVersion {

    @Test
    @DisplayName(
        "Given an approved action, a content edit by an author should create a pending version and keep running the approved content")
    void given_authorContentEdit_should_createPendingVersionAndKeepApprovedContent()
        throws Exception {
      // Arrange
      String actionId = approvedAction();
      String approvedContent = commandContent(actionId);

      // Act
      updateAs(
              author,
              actionId,
              update("echo changed", "Description", Endpoint.PLATFORM_TYPE.Windows))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Assert: the action and its contract keep the approved content
      String detail = detail(actionId);
      assertThat((String) JsonPath.read(detail, "$.action_approval_status")).isEqualTo("APPROVED");
      assertThat((String) JsonPath.read(detail, "$.command_content")).isEqualTo(approvedContent);
      assertThat(injectorContractRepository.findById(actionId).orElseThrow().getPlatforms())
          .containsExactly(Endpoint.PLATFORM_TYPE.Linux);
      // Assert: the pending version holds the edit
      assertThat((Integer) JsonPath.read(detail, "$.action_active_version")).isEqualTo(1);
      assertThat((Integer) JsonPath.read(detail, "$.action_pending_version.version_number"))
          .isEqualTo(2);
      assertThat((String) JsonPath.read(detail, "$.action_pending_version.version_origin"))
          .isEqualTo("UPDATE");
      assertThat((String) JsonPath.read(detail, "$.action_pending_version.version_author_name"))
          .startsWith("Author");
      assertThat((String) JsonPath.read(detail, "$.action_pending_version.version_content.content"))
          .isEqualTo("echo changed");
      assertThat((String) JsonPath.read(detail, "$.action_active_content.content"))
          .isEqualTo(approvedContent);
    }

    @Test
    @DisplayName(
        "Given an edit changing the description and the content, the description should apply now and the content wait for approval")
    void given_mixedEdit_should_applyCosmeticPartNow() throws Exception {
      // Arrange
      String actionId = approvedAction();
      String approvedContent = commandContent(actionId);

      // Act
      updateAs(author, actionId, update("echo changed", "A clearer description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Assert
      String detail = detail(actionId);
      assertThat((String) JsonPath.read(detail, "$.action_description"))
          .isEqualTo("A clearer description");
      assertThat((String) JsonPath.read(detail, "$.command_content")).isEqualTo(approvedContent);
      assertThat((Object) JsonPath.read(detail, "$.action_pending_version")).isNotNull();
    }

    @Test
    @DisplayName("Given a cosmetic edit, it should apply directly without a version")
    void given_cosmeticEdit_should_applyWithoutVersion() throws Exception {
      // Arrange
      String actionId = approvedAction();

      // Act
      updateAs(author, actionId, update(commandContent(actionId), "A clearer description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Assert
      String detail = detail(actionId);
      assertThat((String) JsonPath.read(detail, "$.action_description"))
          .isEqualTo("A clearer description");
      assertThat((Object) JsonPath.read(detail, "$.action_pending_version")).isNull();
      assertThat((List<Object>) JsonPath.read(versions(actionId), "$")).isEmpty();
    }

    @Test
    @DisplayName(
        "Given a pending version, a new content edit should supersede it, and the same edit again should change nothing")
    void given_newEdit_should_supersedePendingVersion() throws Exception {
      // Arrange
      String actionId = approvedAction();
      updateAs(author, actionId, update("echo first", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Act
      updateAs(author, actionId, update("echo second", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();
      updateAs(author, actionId, update("echo second", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Assert: one pending version, the previous one superseded
      String versions = versions(actionId);
      assertThat((List<String>) JsonPath.read(versions, "$[*].version_status"))
          .containsExactly("PENDING", "SUPERSEDED");
      assertThat((List<Integer>) JsonPath.read(versions, "$[*].version_number"))
          .containsExactly(3, 2);
      assertThat(
              (String)
                  JsonPath.read(
                      detail(actionId), "$.action_pending_version.version_content.content"))
          .isEqualTo("echo second");
    }

    @Test
    @DisplayName(
        "Given a pending version, a content edit by an Approve content holder should apply directly and supersede it (US5.4)")
    void given_approverEdit_should_applyDirectlyAndSupersedePending() throws Exception {
      // Arrange
      String actionId = approvedAction();
      updateAs(author, actionId, update("echo by author", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Act
      updateAs(approver, actionId, update("echo by approver", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Assert
      String detail = detail(actionId);
      assertThat((String) JsonPath.read(detail, "$.action_approval_status")).isEqualTo("APPROVED");
      assertThat((String) JsonPath.read(detail, "$.command_content")).isEqualTo("echo by approver");
      assertThat((Object) JsonPath.read(detail, "$.action_pending_version")).isNull();
      assertThat((Integer) JsonPath.read(detail, "$.action_active_version")).isEqualTo(3);
      assertThat((List<String>) JsonPath.read(versions(actionId), "$[*].version_status"))
          .containsExactly("APPROVED", "SUPERSEDED");
    }

    @Test
    @DisplayName(
        "Given an action that was never approved, a content edit should apply in place without a version")
    void given_neverApprovedAction_should_beEditedInPlace() throws Exception {
      // Arrange
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      String created =
          mvc.perform(
                  post(url(""))
                      .with(authentication(author))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          asJsonString(
                              ThreatArsenalInputFixture.createDefaultCommandLineAction(
                                  List.of(domain.getId())))))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String actionId = JsonPath.read(created, "$.injector_contract_id");

      // Act
      updateAs(author, actionId, update("echo changed", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Assert
      String detail = detail(actionId);
      assertThat((String) JsonPath.read(detail, "$.action_approval_status")).isEqualTo("PENDING");
      assertThat((String) JsonPath.read(detail, "$.command_content")).isEqualTo("echo changed");
      assertThat((Object) JsonPath.read(detail, "$.action_pending_version")).isNull();
    }

    @Test
    @DisplayName(
        "Given an action with a pending version, the list should flag it and the pending version filter should find it")
    void given_pendingVersion_should_beFlaggedAndFilterable() throws Exception {
      // Arrange
      String withVersion = approvedAction();
      String without = approvedAction();
      updateAs(author, withVersion, update("echo changed", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();
      Filters.Filter filter = new Filters.Filter();
      filter.setKey("action_payload_pending_version");
      filter.setOperator(Filters.FilterOperator.eq);
      filter.setMode(Filters.FilterMode.or);
      filter.setValues(List.of("true"));
      Filters.FilterGroup filterGroup = new Filters.FilterGroup();
      filterGroup.setMode(Filters.FilterMode.and);
      filterGroup.setFilters(new ArrayList<>(List.of(filter)));

      // Act
      String page =
          mvc.perform(
                  post(url("/search"))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          asJsonString(
                              PaginationFixture.getDefault()
                                  .size(1000)
                                  .filterGroup(filterGroup)
                                  .build())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(page, "$.content[*].injector_contract_id");
      assertThat(ids).contains(withVersion).doesNotContain(without);
      assertThat(
              (List<Boolean>)
                  JsonPath.read(page, "$.content[*].action_payload.payload_pending_version"))
          .containsOnly(true);
      assertThat(
              (List<String>)
                  JsonPath.read(page, "$.content[*].action_payload.payload_approval_status"))
          .containsOnly("APPROVED");
    }
  }

  @Nested
  @DisplayName("Approve or reject a pending version (US5.2)")
  class Decisions {

    @Test
    @DisplayName(
        "Given a pending version, approving it with its fingerprint should apply it to the action and its contract")
    void given_approve_should_applyVersion() throws Exception {
      // Arrange
      String actionId = approvedAction();
      updateAs(
              author,
              actionId,
              update("echo changed", "Description", Endpoint.PLATFORM_TYPE.Windows))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Act
      approveAs(approver, actionId, pendingFingerprint(actionId))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Assert
      String detail = detail(actionId);
      assertThat((String) JsonPath.read(detail, "$.command_content")).isEqualTo("echo changed");
      assertThat((String) JsonPath.read(detail, "$.action_approval_status")).isEqualTo("APPROVED");
      assertThat((Object) JsonPath.read(detail, "$.action_pending_version")).isNull();
      assertThat((Integer) JsonPath.read(detail, "$.action_active_version")).isEqualTo(2);
      assertThat((String) JsonPath.read(detail, "$.action_approval_latest.approval_origin"))
          .isEqualTo("APPROVE");
      assertThat(injectorContractRepository.findById(actionId).orElseThrow().getPlatforms())
          .containsExactly(Endpoint.PLATFORM_TYPE.Windows);
      assertThat((List<String>) JsonPath.read(versions(actionId), "$[*].version_status"))
          .containsExactly("APPROVED");
    }

    @Test
    @DisplayName(
        "Given a pending version, approving with the fingerprint of the active content should be refused")
    void given_staleFingerprint_should_refuseApproval() throws Exception {
      // Arrange
      String actionId = approvedAction();
      updateAs(author, actionId, update("echo changed", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();
      String activeFingerprint = JsonPath.read(detail(actionId), "$.action_approval_fingerprint");

      // Act & Assert
      approveAs(approver, actionId, activeFingerprint).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "Given a pending version, rejecting it should need a reason and keep the approved content")
    void given_reject_should_keepActiveVersion() throws Exception {
      // Arrange
      String actionId = approvedAction();
      String approvedContent = commandContent(actionId);
      updateAs(author, actionId, update("echo changed", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Act & Assert: no reason
      rejectAs(approver, actionId, " ").andExpect(status().isBadRequest());

      // Act
      rejectAs(approver, actionId, "Too broad").andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Assert
      String detail = detail(actionId);
      assertThat((String) JsonPath.read(detail, "$.action_approval_status")).isEqualTo("APPROVED");
      assertThat((String) JsonPath.read(detail, "$.command_content")).isEqualTo(approvedContent);
      assertThat((Object) JsonPath.read(detail, "$.action_pending_version")).isNull();
      String versions = versions(actionId);
      assertThat((List<String>) JsonPath.read(versions, "$[*].version_status"))
          .containsExactly("REJECTED");
      assertThat((List<String>) JsonPath.read(versions, "$[*].version_comment"))
          .containsExactly("Too broad");
    }

    @Test
    @DisplayName("Given a pending version, an author without Approve content should not decide it")
    void given_author_should_notDecide() throws Exception {
      // Arrange
      String actionId = approvedAction();
      updateAs(author, actionId, update("echo changed", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Act & Assert
      approveAs(author, actionId, pendingFingerprint(actionId)).andExpect(status().isForbidden());
      rejectAs(author, actionId, "No").andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Collector updates (US5.4)")
  class Collector {

    private void upsert(String externalId, String content) throws Exception {
      PayloadUpsertInput input = PayloadInputFixture.getDefaultCommandPayloadUpsertInput(Set.of());
      input.setExternalId(externalId);
      input.setContent(content);
      mvc.perform(
              post(PAYLOAD_URI + "/upsert")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();
    }

    @Test
    @DisplayName(
        "Given an approved collector payload, a changed update should create a pending version, the same update again should change nothing, and another one should supersede it")
    void given_collectorUpdates_should_createAndSupersedePendingVersions() throws Exception {
      // Arrange: collected, then approved
      String externalId = "collector-" + UUID.randomUUID();
      upsert(externalId, "cd ..");
      Payload payload = payloadRepository.findByExternalId(externalId).orElseThrow();
      String actionId =
          injectorContractRepository.findInjectorContractByPayload(payload).orElseThrow().getId();
      approveAs(
              approver, actionId, JsonPath.read(detail(actionId), "$.action_approval_fingerprint"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Act
      upsert(externalId, "cd /tmp");
      String firstPending = JsonPath.read(detail(actionId), "$.action_pending_version.version_id");
      upsert(externalId, "cd /tmp");

      // Assert: one pending version, origin collector, the action still runs "cd .."
      String detail = detail(actionId);
      assertThat((String) JsonPath.read(detail, "$.command_content")).isEqualTo("cd ..");
      assertThat((String) JsonPath.read(detail, "$.action_pending_version.version_id"))
          .isEqualTo(firstPending);
      assertThat((String) JsonPath.read(detail, "$.action_pending_version.version_origin"))
          .isEqualTo("COLLECTOR");

      // Act
      upsert(externalId, "cd /var");

      // Assert
      assertThat((List<String>) JsonPath.read(versions(actionId), "$[*].version_status"))
          .containsExactly("PENDING", "SUPERSEDED");
    }
  }

  @Nested
  @DisplayName("Launch state while a version is pending (US5.1, US5.5)")
  class LaunchState {

    @Test
    @DisplayName(
        "Given an action in use, a pending version should block nothing: a planned simulation keeps its start and nothing names the action as blocking")
    void given_pendingVersion_should_blockNothing() throws Exception {
      // Arrange
      String actionId = approvedAction();
      String atomicId = injectUsing(actionId, "Atomic whoami").persist().get().getId();
      String scenarioId =
          scenarioComposer
              .forScenario(ScenarioFixture.getScenario())
              .withInject(injectUsing(actionId, "Scenario inject"))
              .persist()
              .get()
              .getId();
      String plannedId =
          simulationWithInject(
                  "Planned",
                  ExerciseStatus.SCHEDULED,
                  Instant.now().plus(1, ChronoUnit.DAYS),
                  actionId,
                  false)
              .getId();

      // Act
      updateAs(author, actionId, update("echo changed", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Assert
      assertThat(exerciseRepository.findById(plannedId).orElseThrow().getStart()).isPresent();
      assertThat(
              (List<Object>)
                  JsonPath.read(
                      getJson(ATOMIC_TESTING_URI + "/" + atomicId), "$.inject_launch_blocked_by"))
          .isEmpty();
      assertThat(
              (List<Object>)
                  JsonPath.read(
                      getJson(SCENARIO_URI + "/" + scenarioId), "$.scenario_launch_blocked_by"))
          .isEmpty();
      assertThat(
              (List<Object>)
                  JsonPath.read(
                      getJson(EXERCISE_URI + "/" + plannedId), "$.exercise_launch_blocked_by"))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("Paused schedules: only a sensitive inject change pauses")
  class PausedSchedules {

    private String recurringScenario(String actionId) {
      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setRecurrence("0 0 * * * *");
      return scenarioComposer
          .forScenario(scenario)
          .withInject(injectUsing(actionId, "Scenario inject"))
          .persist()
          .get()
          .getId();
    }

    private Object scenarioPausedAt(String scenarioId) throws Exception {
      return JsonPath.read(
          getJson(SCENARIO_URI + "/" + scenarioId), "$.scenario_recurrence_paused_at");
    }

    @Test
    @DisplayName(
        "Given an approved action with recurring schedules, an edit by an author should pause nothing (US5.5)")
    void given_editOfApprovedAction_should_pauseNothing() throws Exception {
      // Arrange
      String actionId = approvedAction();
      String scenarioId = recurringScenario(actionId);
      Inject recurringAtomic = InjectFixture.getDefaultInject();
      recurringAtomic.setTitle("Recurring atomic");
      recurringAtomic.setRecurrence("0 0 * * * *");
      recurringAtomic.setInjectorContract(
          injectorContractRepository.findById(actionId).orElseThrow());
      String atomicId = injectComposer.forInject(recurringAtomic).persist().get().getId();

      // Act
      updateAs(author, actionId, update("echo changed", "Description"))
          .andExpect(status().is2xxSuccessful());
      flushAndClear();

      // Assert
      assertThat(scenarioPausedAt(scenarioId)).isNull();
      assertThat(
              (Object)
                  JsonPath.read(
                      getJson(ATOMIC_TESTING_URI + "/" + atomicId),
                      "$.inject_recurrence_paused_at"))
          .isNull();
    }

    @Test
    @DisplayName(
        "Given a sensitive change of an inject, its recurring scenario should be paused and a planned simulation should go back to draft; a title change should change nothing")
    void given_sensitiveChange_should_pauseOrUnplan() throws Exception {
      // Arrange
      String actionId = approvedAction();
      String scenarioId = recurringScenario(actionId);
      Inject scenarioInject =
          injectRepository.findByScenarioId(scenarioId).stream().findFirst().orElseThrow();
      String plannedId =
          simulationWithInject(
                  "Planned",
                  ExerciseStatus.SCHEDULED,
                  Instant.now().plus(1, ChronoUnit.DAYS),
                  actionId,
                  false)
              .getId();
      Inject simulationInject =
          injectRepository.findByExerciseId(plannedId).stream().findFirst().orElseThrow();

      // Act: title only
      injectService.updateInject(
          scenarioInject.getId(), inputFrom(scenarioInject, "New title", null));
      entityManager.flush();
      entityManager.clear();

      // Assert: nothing paused
      assertThat(scenarioPausedAt(scenarioId)).isNull();

      // Act: content changed (scenario) and targets changed (planned simulation)
      Inject reloaded = injectRepository.findById(scenarioInject.getId()).orElseThrow();
      ObjectNode content = new ObjectMapper().createObjectNode().put("expectation", "changed");
      injectService.updateInject(
          reloaded.getId(), inputFrom(reloaded, reloaded.getTitle(), content));
      Inject simulationReloaded = injectRepository.findById(simulationInject.getId()).orElseThrow();
      InjectInput allTeams = inputFrom(simulationReloaded, simulationReloaded.getTitle(), null);
      allTeams.setAllTeams(true);
      injectService.updateInject(simulationReloaded.getId(), allTeams);
      entityManager.flush();
      entityManager.clear();

      // Assert
      assertThat(scenarioPausedAt(scenarioId)).isNotNull();
      assertThat(exerciseRepository.findById(plannedId).orElseThrow().getStart()).isEmpty();
    }

    private InjectInput inputFrom(Inject inject, String title, ObjectNode content) {
      InjectInput input = new InjectInput();
      input.setTitle(title);
      input.setInjectorContract(inject.getInjectorContract().orElseThrow().getId());
      input.setContent(content != null ? content : inject.getContent());
      input.setDependsDuration(inject.getDependsDuration());
      input.setAllTeams(inject.isAllTeams());
      return input;
    }
  }
}
