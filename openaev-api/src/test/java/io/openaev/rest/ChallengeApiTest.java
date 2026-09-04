package io.openaev.rest;

import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.ChallengeFixture.createDefaultChallenge;
import static io.openaev.utils.fixtures.InjectFixture.createDefaultInjectChallenge;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.integration.impl.injectors.challenge.ChallengeInjectorIntegrationFactory;
import io.openaev.rest.challenge.form.ChallengeInput;
import io.openaev.rest.challenge.form.FlagInput;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
@Transactional
class ChallengeApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ScenarioService scenarioService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private ChallengeInjectorIntegrationFactory challengeInjectorIntegrationFactory;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;
  @Resource private ObjectMapper objectMapper;

  // -- SCENARIOS --

  @DisplayName("Retrieve challenges for scenario")
  @Test
  @WithMockUser(isAdmin = true)
  void retrieveChallengesVariableForScenarioTest() throws Exception {
    // -- PREPARE --
    challengeInjectorIntegrationFactory.registerConnectorForTenant(
        TenantContext.getCurrentTenant());

    Scenario scenario = createDefaultCrisisScenario();
    Scenario scenarioCreated = this.scenarioService.createScenario(scenario);
    assertNotNull(scenarioCreated, "Scenario should be successfully created");
    String SCENARIO_ID = scenarioCreated.getId();

    Challenge challenge = createDefaultChallenge();
    Challenge challengeCreated = this.challengeRepository.save(challenge);
    assertNotNull(challengeCreated, "Challenge should be successfully created");
    String CHALLENGE_ID = challengeCreated.getId();

    Inject inject =
        createDefaultInjectChallenge(
            this.injectorContractRepository.findById(CHALLENGE_PUBLISH).orElseThrow(),
            this.objectMapper,
            List.of(CHALLENGE_ID));
    inject.setScenario(scenarioCreated);
    Inject injectCreated = this.injectRepository.save(inject);
    assertNotNull(injectCreated, "Inject should be successfully created");
    String INJECT_ID = injectCreated.getId();

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                get(SCENARIO_URI + "/" + SCENARIO_ID + "/challenges")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response, "Response should not be null");
    assertEquals(
        challenge.getName(),
        JsonPath.read(response, "$[0].challenge_name"),
        "Challenge name should match the expected value");
  }

  // -- TENANT ISOLATION TESTS --

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(isAdmin = true)
  class TenantIsolation {

    private ChallengeInput createChallengeInput(String name) {
      FlagInput flag = new FlagInput();
      flag.setType("VALUE");
      flag.setValue("secret-flag");
      return new ChallengeInput(
          name, "category", "content", 100.0, 3, List.of(), List.of(), List.of(flag));
    }

    private String createChallengeInTenant(String tenantId, String name) throws Exception {
      ChallengeInput input = createChallengeInput(name);

      String response =
          mvc.perform(
                  post("/api/tenants/" + tenantId + "/challenges")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      return JsonPath.read(response, "$.challenge_id");
    }

    @Test
    @DisplayName("Challenge created in tenant X should NOT be updatable from tenant Y")
    void given_challengeInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_CHALLENGES, Capability.ACCESS_CHALLENGES));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_CHALLENGES, Capability.ACCESS_CHALLENGES));

      String challengeId = createChallengeInTenant(tenantX.getId(), "Update Isolation Challenge");

      entityManager.flush();
      entityManager.clear();

      // Act — update from tenant Y
      ChallengeInput updateInput = createChallengeInput("Hijacked Challenge");

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/" + tenantY.getId() + "/challenges/" + challengeId)
                      .content(asJsonString(updateInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Challenge created in tenant X should be updatable from tenant X")
    void given_challengeInTenantX_should_beUpdatableFromTenantX() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_CHALLENGES, Capability.ACCESS_CHALLENGES));

      String challengeId = createChallengeInTenant(tenantX.getId(), "Same Tenant Challenge");

      // Act — update from same tenant
      ChallengeInput updateInput = createChallengeInput("Updated Challenge");

      String response =
          mvc.perform(
                  put("/api/tenants/" + tenantX.getId() + "/challenges/" + challengeId)
                      .content(asJsonString(updateInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertEquals("Updated Challenge", JsonPath.read(response, "$.challenge_name"));
    }

    @Test
    @DisplayName("Challenge created in tenant X should NOT be deletable from tenant Y")
    void given_challengeInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_CHALLENGES, Capability.ACCESS_CHALLENGES));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.DELETE_CHALLENGES, Capability.ACCESS_CHALLENGES));

      String challengeId = createChallengeInTenant(tenantX.getId(), "Delete Isolation Challenge");

      entityManager.flush();
      entityManager.clear();

      // Act — delete from tenant Y
      int responseStatus =
          mvc.perform(
                  delete("/api/tenants/" + tenantY.getId() + "/challenges/" + challengeId)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Challenge created in tenant X should NOT be tryable from tenant Y")
    void given_challengeInTenantX_should_notBeTryableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_CHALLENGES, Capability.ACCESS_CHALLENGES));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_CHALLENGES, Capability.ACCESS_CHALLENGES));

      // Seeded directly (native insert), not through the create endpoint: creating under tenant
      // X's path would set the tenant scope (TxCtx) to X on this test's wrapping transaction, and
      // the try call below sets it to Y - the aspect refuses a scope change within one
      // transaction (see TenantScopeTransactionAspect). Seeding bypasses that entirely.
      String challengeId = UUID.randomUUID().toString();
      entityManager
          .createNativeQuery(
              "INSERT INTO challenges (challenge_id, challenge_name, tenant_id)"
                  + " VALUES (:id, :name, CAST(:tenant AS uuid))")
          .setParameter("id", challengeId)
          .setParameter("name", "Try Isolation Challenge")
          .setParameter("tenant", tenantX.getId())
          .executeUpdate();

      entityManager.flush();
      entityManager.clear();

      // Act — try from tenant Y
      String tryInput = "{\"challenge_value\": \"secret-flag\"}";

      int responseStatus =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/challenges/" + challengeId + "/try")
                      .content(tryInput)
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
  }
}
