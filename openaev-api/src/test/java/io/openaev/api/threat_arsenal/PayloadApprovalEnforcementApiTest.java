package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Payload approval enforcement: picker, inject creation and launch")
class PayloadApprovalEnforcementApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private DomainComposer domainComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  @BeforeEach
  void beforeEach() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    domainComposer.reset();
    payloadComposer.reset();
    injectorContractComposer.reset();
    injectComposer.reset();
    scenarioComposer.reset();
  }

  private InjectorContractComposer.Composer contract(
      String payloadName, PAYLOAD_APPROVAL_STATUS s) {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setName(payloadName);
    payload.setApprovalStatus(s);
    return injectorContractComposer
        .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
        .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
        .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()).persist())
        .withPayload(payloadComposer.forPayload(payload));
  }

  private InjectorContractComposer.Composer payloadLessContract() {
    return injectorContractComposer
        .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
        .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
        .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()).persist());
  }

  private InjectComposer.Composer injectWith(InjectorContractComposer.Composer contract) {
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withInjectorContract(contract);
  }

  private static String message(String body) {
    return JsonPath.read(body, "$.message");
  }

  @Nested
  @DisplayName("Picker (US2.1 AC1)")
  class Picker {

    private List<String> searchIds(String uri, boolean approvedOnly, List<String> ids)
        throws Exception {
      InjectorContractSearchPaginationInput input = new InjectorContractSearchPaginationInput();
      input.setPage(0);
      input.setSize(20);
      input.setIncludeFullDetails(false);
      input.setApprovedPayloadsOnly(approvedOnly);
      input.setInjectorContractIdsToProcess(ids);
      String response =
          mvc.perform(
                  post(uri)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return ids.stream().filter(response::contains).toList();
    }

    @Test
    @DisplayName(
        "Given approved_payloads_only, the action search should list approved and payload-less actions only")
    void given_approvedPayloadsOnly_should_listApprovedAndPayloadLessActionsOnly()
        throws Exception {
      String pending = contract("Pending", PAYLOAD_APPROVAL_STATUS.PENDING).persist().get().getId();
      String rejected =
          contract("Rejected", PAYLOAD_APPROVAL_STATUS.REJECTED).persist().get().getId();
      String approved =
          contract("Approved", PAYLOAD_APPROVAL_STATUS.APPROVED).persist().get().getId();
      String payloadLess = payloadLessContract().persist().get().getId();
      List<String> all = List.of(pending, rejected, approved, payloadLess);

      assertThat(searchIds(INJECTOR_CONTRACT_URL + "/search", true, all))
          .containsExactlyInAnyOrder(approved, payloadLess);
      assertThat(searchIds(THREAT_ARSENAL_URL + "/search", true, all))
          .containsExactlyInAnyOrder(approved, payloadLess);
    }

    @Test
    @DisplayName("Without approved_payloads_only, the action search should still list every action")
    void given_noFlag_should_listEveryAction() throws Exception {
      String pending = contract("Pending", PAYLOAD_APPROVAL_STATUS.PENDING).persist().get().getId();
      String approved =
          contract("Approved", PAYLOAD_APPROVAL_STATUS.APPROVED).persist().get().getId();

      assertThat(searchIds(INJECTOR_CONTRACT_URL + "/search", false, List.of(pending, approved)))
          .containsExactlyInAnyOrder(pending, approved);
    }
  }

  @Nested
  @DisplayName("Inject creation")
  class InjectCreation {

    private org.springframework.test.web.servlet.ResultActions addInject(
        Scenario scenario, InjectorContract contract) throws Exception {
      InjectInput input = new InjectInput();
      input.setTitle("Inject");
      input.setInjectorContract(contract.getId());
      input.setDependsDuration(0L);
      return mvc.perform(
          post(SCENARIO_URI + "/" + scenario.getId() + "/injects")
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(input))
              .with(csrf()));
    }

    @Test
    @DisplayName("Given a pending payload, adding it to a scenario should be refused")
    void given_pendingPayload_should_refuseAddingIt() throws Exception {
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
      InjectorContract pending =
          contract("Mimikatz", PAYLOAD_APPROVAL_STATUS.PENDING).persist().get();

      String body =
          addInject(scenario, pending)
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(message(body))
          .startsWith("Adding this action to an inject is blocked")
          .contains("\"Mimikatz\" (pending approval)");
    }

    @Test
    @DisplayName("Given an approved payload, adding it to a scenario should succeed")
    void given_approvedPayload_should_addIt() throws Exception {
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
      InjectorContract approved =
          contract("Whoami", PAYLOAD_APPROVAL_STATUS.APPROVED).persist().get();

      addInject(scenario, approved).andExpect(status().is2xxSuccessful());
    }
  }

  @Nested
  @DisplayName("Launch (US2.2)")
  class Launch {

    @Test
    @DisplayName(
        "Given a scenario using pending and rejected payloads, launching it should be refused and list both")
    void given_scenarioWithUnapprovedPayloads_should_refuseLaunchListingThem() throws Exception {
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.getScenario())
              .withInject(injectWith(contract("Mimikatz", PAYLOAD_APPROVAL_STATUS.PENDING)))
              .withInject(injectWith(contract("Rubeus", PAYLOAD_APPROVAL_STATUS.REJECTED)))
              .withInject(injectWith(contract("Whoami", PAYLOAD_APPROVAL_STATUS.APPROVED)))
              .persist()
              .get();

      String body =
          mvc.perform(
                  post(SCENARIO_URI + "/" + scenario.getId() + "/exercise/running").with(csrf()))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(message(body))
          .startsWith("Launching the scenario \"" + scenario.getName() + "\" is blocked")
          .contains("\"Mimikatz\" (pending approval)", "\"Rubeus\" (rejected)")
          .doesNotContain("Whoami");
    }

    @Test
    @DisplayName("Given an atomic testing using a pending payload, launching it should be refused")
    void given_atomicTestingWithPendingPayload_should_refuseLaunch() throws Exception {
      Inject atomicTesting =
          injectWith(contract("Mimikatz", PAYLOAD_APPROVAL_STATUS.PENDING)).persist().get();

      String body =
          mvc.perform(
                  post(ATOMIC_TESTING_URI + "/" + atomicTesting.getId() + "/launch").with(csrf()))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(message(body)).contains("is blocked", "\"Mimikatz\" (pending approval)");
    }
  }

  @Nested
  @DisplayName("Inject list (US2.1 AC4)")
  class InjectList {

    @Test
    @DisplayName(
        "Given injects not run yet, the scenario inject list should carry each payload approval status and no sent date")
    void given_injectsNotRunYet_should_carryPayloadApprovalStatusAndNoSentDate() throws Exception {
      // Arrange
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.getScenario())
              .withInject(injectWith(contract("Mimikatz", PAYLOAD_APPROVAL_STATUS.PENDING)))
              .withInject(injectWith(contract("Whoami", PAYLOAD_APPROVAL_STATUS.APPROVED)))
              .persist()
              .get();

      // Act
      String body =
          mvc.perform(get(SCENARIO_URI + "/" + scenario.getId() + "/injects/simple"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(
              (List<String>)
                  JsonPath.read(
                      body,
                      "$[*].inject_injector_contract.injector_contract_payload.payload_approval_status"))
          .containsExactlyInAnyOrder("PENDING", "APPROVED");
      assertThat((List<Object>) JsonPath.read(body, "$[*].inject_sent_at"))
          .containsOnlyNulls()
          .hasSize(2);
    }
  }
}
