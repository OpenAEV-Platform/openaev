package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
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

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
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
}
