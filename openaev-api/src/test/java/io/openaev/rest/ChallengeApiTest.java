package io.openaev.rest;

import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.fixtures.ChallengeFixture.createDefaultChallenge;
import static io.openaev.utils.fixtures.InjectFixture.createDefaultInjectChallenge;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
class ChallengeApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ScenarioService scenarioService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Resource private ObjectMapper objectMapper;

  private static String SCENARIO_ID;
  private static String CHALLENGE_ID;
  private static String INJECT_ID;

  @AfterAll
  void afterAll() {
    this.scenarioService.deleteScenario(SCENARIO_ID);
    this.challengeRepository.deleteById(CHALLENGE_ID);
    this.injectRepository.deleteById(INJECT_ID);
  }

  // -- SCENARIOS --

  @DisplayName("Retrieve challenges for scenario")
  @Test
  @Order(1)
  @WithMockUser(isAdmin = true)
  void retrieveChallengesVariableForScenarioTest() throws Exception {
    // -- PREPARE --
    Scenario scenario = createDefaultCrisisScenario();
    Scenario scenarioCreated = this.scenarioService.createScenario(scenario);
    assertNotNull(scenarioCreated, "Scenario should be successfully created");
    SCENARIO_ID = scenarioCreated.getId();

    Challenge challenge = createDefaultChallenge();
    Challenge challengeCreated = this.challengeRepository.save(challenge);
    assertNotNull(challengeCreated, "Challenge should be successfully created");
    CHALLENGE_ID = challengeCreated.getId();

    Inject inject =
        createDefaultInjectChallenge(
            this.injectorContractRepository.findById(CHALLENGE_PUBLISH).orElseThrow(),
            this.objectMapper,
            List.of(CHALLENGE_ID));
    inject.setScenario(scenarioCreated);
    Inject injectCreated = this.injectRepository.save(inject);
    assertNotNull(injectCreated, "Inject should be successfully created");
    INJECT_ID = injectCreated.getId();

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
}
