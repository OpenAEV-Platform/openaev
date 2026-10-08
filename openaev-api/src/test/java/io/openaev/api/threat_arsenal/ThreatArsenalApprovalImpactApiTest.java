package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
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
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
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
@DisplayName("Threat Arsenal payload approval: warning before impact (US2.4)")
class ThreatArsenalApprovalImpactApiTest extends IntegrationTest {

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

  private void approveAs(Authentication as, String actionId) throws Exception {
    String fingerprint = JsonPath.read(detail(actionId), "$.action_approval_fingerprint");
    mvc.perform(
            post(url("/" + actionId + "/approve"))
                .with(authentication(as))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(Map.of("approval_fingerprint", fingerprint))))
        .andExpect(status().is2xxSuccessful());
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
    return new ThreatArsenalActionUpdateInput(
        "Command line payload",
        new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
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
      Authentication as, String actionId, ThreatArsenalActionUpdateInput input, boolean check)
      throws Exception {
    return mvc.perform(
        put(url("/" + actionId))
            .param("check_approval_impact", String.valueOf(check))
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

  private String approvalStatus(String actionId) throws Exception {
    return JsonPath.read(detail(actionId), "$.action_approval_status");
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
  @DisplayName("Edit sending an approved payload in use back to pending")
  class EditWarning {

    @Test
    @DisplayName(
        "Given check_approval_impact, a content edit of a used payload by an author should be refused with the usage and save nothing")
    void given_checkAndContentEditOfUsedPayload_should_refuseWithUsageAndSaveNothing()
        throws Exception {
      // Arrange
      String actionId = usedAction();

      // Act
      String body =
          updateAs(author, actionId, update("echo changed", "Description"), true)
              .andExpect(status().isConflict())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat((String) JsonPath.read(body, "$.message"))
          .isEqualTo(
              "Saving will send this payload back to Pending approval and block the launch of 1"
                  + " atomic testing, 1 scenario, 1 simulation until it is approved again.");
      assertThat((Integer) JsonPath.read(body, "$.usage.usage_simulations_count")).isEqualTo(1);
      // The test shares one transaction with the request: drop the refused in-memory edit and
      // re-read what the database holds (a real request rolls its own transaction back).
      entityManager.clear();
      assertThat(approvalStatus(actionId)).isEqualTo("APPROVED");
      assertThat((String) JsonPath.read(detail(actionId), "$.command_content"))
          .isNotEqualTo("echo changed");
    }

    @Test
    @DisplayName("Without check_approval_impact, the same edit should be saved and become pending")
    void given_noCheck_should_saveAndBecomePending() throws Exception {
      // Arrange
      String actionId = usedAction();

      // Act
      updateAs(author, actionId, update("echo changed", "Description"), false)
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Given check_approval_impact, a cosmetic edit should be saved and stay approved")
    void given_checkAndCosmeticEdit_should_saveAndStayApproved() throws Exception {
      // Arrange
      String actionId = usedAction();
      String content = JsonPath.read(detail(actionId), "$.command_content");

      // Act
      updateAs(author, actionId, update(content, "A clearer description"), true)
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(approvalStatus(actionId)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Given check_approval_impact, a content edit of an unused payload should be saved")
    void given_checkAndUnusedPayload_should_save() throws Exception {
      // Arrange
      String actionId = approvedAction();

      // Act
      updateAs(author, actionId, update("echo changed", "Description"), true)
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName(
        "Given check_approval_impact, a content edit by an Approve content holder should be saved and stay approved")
    void given_checkAndApproverEdit_should_saveAndStayApproved() throws Exception {
      // Arrange
      String actionId = usedAction();

      // Act
      updateAs(approver, actionId, update("echo changed", "Description"), true)
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(approvalStatus(actionId)).isEqualTo("APPROVED");
    }
  }

  @Nested
  @DisplayName("Both warnings use the same usage (item 2)")
  class SameUsage {

    @Test
    @DisplayName(
        "Given a used payload, the edit warning and the reject dialog should report the same usage, counting only simulations where it has not run yet")
    void given_usedPayload_should_reportTheSameUsageInBothDialogs() throws Exception {
      // Arrange: usedAction() = 1 atomic testing, 1 scenario, 1 scheduled + 1 finished simulation
      String actionId = usedAction();
      simulationWithInject(
          "Running, not run yet", ExerciseStatus.RUNNING, Instant.now(), actionId, false);
      simulationWithInject(
          "Running, already ran", ExerciseStatus.RUNNING, Instant.now(), actionId, true);

      // Act: what the reject dialog reads, then what the edit warning gets, for the same user
      Object rejectUsage =
          JsonPath.read(
              mvc.perform(get(url("/" + actionId + "/usage")).with(authentication(author)))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString(),
              "$");
      String conflict =
          updateAs(author, actionId, update("echo changed", "Description"), true)
              .andExpect(status().isConflict())
              .andReturn()
              .getResponse()
              .getContentAsString();
      Object editUsage = JsonPath.read(conflict, "$.usage");

      // Assert
      assertThat(editUsage).isEqualTo(rejectUsage);
      assertThat((Integer) JsonPath.read(conflict, "$.usage.usage_simulations_count")).isEqualTo(2);
      // Names, for a user who can open simulations: finished and already-run ones are not listed
      assertThat(
              (List<String>)
                  JsonPath.read(
                      getJson(url("/" + actionId + "/usage")), "$.usage_simulations[*].name"))
          .containsExactlyInAnyOrder("Scheduled simulation", "Running, not run yet");
    }
  }

  @Nested
  @DisplayName("Launch state when an action becomes pending (items 3 and 4)")
  class LaunchState {

    @Test
    @DisplayName(
        "Given an action that becomes pending, a planned simulation should go back to draft, a running one should keep running, and the GETs should name the blocking payload")
    void given_actionBecomesPending_should_blockLaunchesAndUnplanSimulations() throws Exception {
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
      String runningId =
          simulationWithInject("Running", ExerciseStatus.RUNNING, Instant.now(), actionId, false)
              .getId();
      assertThat(
              (List<Object>)
                  JsonPath.read(
                      getJson(ATOMIC_TESTING_URI + "/" + atomicId), "$.inject_launch_blocked_by"))
          .isEmpty();

      // Act: an author edits what the action runs: it becomes pending
      updateAs(author, actionId, update("echo changed", "Description"), false)
          .andExpect(status().is2xxSuccessful());
      entityManager.flush();
      entityManager.clear();

      // Assert: planned simulation back to draft, running one untouched
      Exercise planned = exerciseRepository.findById(plannedId).orElseThrow();
      assertThat(planned.getStatus()).isEqualTo(ExerciseStatus.SCHEDULED);
      assertThat(planned.getStart()).isEmpty();
      assertThat(exerciseRepository.findById(runningId).orElseThrow().getStatus())
          .isEqualTo(ExerciseStatus.RUNNING);

      // Assert: the three GETs name the blocking payload
      String atomic = getJson(ATOMIC_TESTING_URI + "/" + atomicId);
      assertThat((String) JsonPath.read(atomic, "$.inject_launch_blocked_by[0].name"))
          .isEqualTo("Command line payload");
      assertThat((String) JsonPath.read(atomic, "$.inject_launch_blocked_by[0].approval_status"))
          .isEqualTo("PENDING");
      assertThat(
              (List<Object>)
                  JsonPath.read(
                      getJson(SCENARIO_URI + "/" + scenarioId), "$.scenario_launch_blocked_by"))
          .hasSize(1);
      assertThat(
              (List<Object>)
                  JsonPath.read(
                      getJson(EXERCISE_URI + "/" + plannedId), "$.exercise_launch_blocked_by"))
          .hasSize(1);

      // Act: approved again
      approveAs(approver, actionId);
      entityManager.flush();
      entityManager.clear();

      // Assert: launches are possible again, the simulation stays in draft until planned again
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
      assertThat(exerciseRepository.findById(plannedId).orElseThrow().getStart()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Paused schedules (decisions 1 and 2)")
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

    private ResultActions putRecurrence(String scenarioId, String cron) throws Exception {
      return mvc.perform(
          put(SCENARIO_URI + "/" + scenarioId + "/recurrence")
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(Map.of("scenario_recurrence", cron))));
    }

    private Object scenarioPausedAt(String scenarioId) throws Exception {
      return JsonPath.read(
          getJson(SCENARIO_URI + "/" + scenarioId), "$.scenario_recurrence_paused_at");
    }

    @Test
    @DisplayName(
        "Given an action that becomes pending, recurring scenarios and atomic testings should stay paused after re-approval until a user saves the schedule again")
    void given_actionBecomesPending_should_pauseSchedulesUntilReEnabled() throws Exception {
      // Arrange
      String actionId = approvedAction();
      String scenarioId = recurringScenario(actionId);
      Inject recurringAtomic = InjectFixture.getDefaultInject();
      recurringAtomic.setTitle("Recurring atomic");
      recurringAtomic.setRecurrence("0 0 * * * *");
      recurringAtomic.setInjectorContract(
          injectorContractRepository.findById(actionId).orElseThrow());
      String atomicId = injectComposer.forInject(recurringAtomic).persist().get().getId();
      assertThat(scenarioPausedAt(scenarioId)).isNull();

      // Act: blocked
      updateAs(author, actionId, update("echo changed", "Description"), false)
          .andExpect(status().is2xxSuccessful());
      entityManager.flush();
      entityManager.clear();

      // Assert: both paused, and re-enabling is refused while blocked
      assertThat(scenarioPausedAt(scenarioId)).isNotNull();
      assertThat(
              (Object)
                  JsonPath.read(
                      getJson(ATOMIC_TESTING_URI + "/" + atomicId),
                      "$.inject_recurrence_paused_at"))
          .isNotNull();
      putRecurrence(scenarioId, "0 0 * * * *").andExpect(status().isBadRequest());

      // Act: approved again
      approveAs(approver, actionId);
      entityManager.flush();
      entityManager.clear();

      // Assert: still paused (no automatic resume); saving the schedule re-enables it
      assertThat(scenarioPausedAt(scenarioId)).isNotNull();
      putRecurrence(scenarioId, "0 0 * * * *").andExpect(status().is2xxSuccessful());
      entityManager.flush();
      entityManager.clear();
      assertThat(scenarioPausedAt(scenarioId)).isNull();
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
