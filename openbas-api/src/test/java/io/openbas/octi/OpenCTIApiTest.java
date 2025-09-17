package io.openbas.octi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openbas.IntegrationTest;
import io.openbas.opencti.OpenCTIApi;
import io.openbas.rest.attack_pattern.AttackPatternApi;
import io.openbas.rest.inject.InjectApi;
import io.openbas.rest.inject.ScenarioInjectApi;
import io.openbas.rest.injector_contract.InjectorContractApi;
import io.openbas.rest.kill_chain_phase.KillChainPhaseApi;
import io.openbas.rest.scenario.ScenarioApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Feature: OCTI OBAS - GENERATION SCENARIOS
 *
 * <p>Currently, OCTI uses the following endpoints to simulate scenarios from different types of
 * entities such as (case incidents, groupings, reports, malwares, incidents, campaigns, intrusion
 * sets, threat actor groups, threat actors individuals):
 *
 * <ul>
 *   <li>KillChainPhasesApi -> killChainPhases [GET: /api/kill-chain-phases/]
 *   <li>AttackPatternApi -> attackPatterns [GET: /api/attack_patterns/]
 *   <li>AttackPatternApi -> injectorContracts
 *       [GET:/api/attack_patterns/{attackPatternId}/injector_contracts]
 *   <li>InjectorContractApi -> injectorContracts [POST: /api/injector_contracts/search]
 *   <li>ScenarioApi -> creationScenario [POST: /api/scenarios/]
 *   <li>InjectApi -> createInjectForScenario [POST: /api/injects/{scenarioId}/injects]
 *   <li>OpenCTIApi -> latestExerciseByExternalId
 *       [GET:/api/opencti/v1/exercises/latest/{externalReferenceId}]
 * </ul>
 *
 * <p>These non-regression tests will help us maintain control over any modifications to these
 * endpoints.
 */
@ExtendWith(MockitoExtension.class)
class OpenCTIApiTest extends IntegrationTest {

  @Mock InjectApi injectApi;
  @Mock ScenarioInjectApi scenarioInjectApi;
  @Mock ScenarioApi scenarioApi;
  @Mock KillChainPhaseApi killChainPhaseApi;
  @Mock AttackPatternApi attackPatternApi;
  @Mock InjectorContractApi injectorContractApi;
  @Mock OpenCTIApi openCTIApi;

  private MockMvc mockMvc;

  @BeforeEach
  public void setUp() {
    // Initialize mocks before each test
    MockitoAnnotations.openMocks(this);

    // Set up MockMvc with controllers
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                killChainPhaseApi,
                attackPatternApi,
                injectorContractApi,
                scenarioApi,
                scenarioInjectApi,
                injectApi,
                openCTIApi)
            .build();
  }

  // -- KILL CHAIN PHASES --
  @Test
  @DisplayName("Test to validate existence of the KillChainPhases endpoint")
  public void testGetKillChainPhases_Sucess() throws Exception {
    // -- EXECUTE --
    mockMvc
        .perform(get("/api/kill_chain_phases").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  // -- ATTACK PATTERNS  --
  @Test
  @DisplayName("Test to validate existence of the AttackPatterns endpoint")
  public void testGetAttackPatterns_Sucess() throws Exception {
    // -- EXECUTE --
    mockMvc
        .perform(get("/api/attack_patterns").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  // -- INJECTOR CONTRACTS --
  @Test
  @DisplayName("Test to validate existence of the InjectorContracts endpoint")
  public void testGetInjectorContracts_Success() throws Exception {
    // -- PREPARE --
    String attackPatternId = "attackPatternId";
    // -- EXECUTE --
    mockMvc
        .perform(
            get("/api/attack_patterns/{attackPatternId}/injector_contracts", attackPatternId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Test to validate existence of the InjectorContracts Search endpoint and validate SearchInput")
  public void testSearchInjectorContracts_ValidInput() throws Exception {
    // -- PREPARE --
    String jsonInput =
        "{"
            + "\"page\": 0, "
            + "\"size\": 100, "
            + "\"filterGroup\": {"
            + "  \"filters\": ["
            + "    {\"key\": \"injector_contract_attack_patterns\", \"operator\": \"contains\", \"values\": [\"attackPatternId\"]},"
            + "    {\"key\": \"injector_contract_platforms\", \"operator\": \"contains\", \"values\": [\"platform1\", \"platform2\"]},"
            + "    {\"key\": \"injector_contract_arch\", \"operator\": \"eq\", \"values\": [\"arm64\"]}"
            + "  ],"
            + "  \"mode\": \"and\""
            + "},"
            + "\"textSearch\": \"searchText\", "
            + "\"sorts\": [{\"property\": \"label\", \"direction\": \"asc\"}]"
            + "}";
    // -- EXECUTE --
    mockMvc
        .perform(
            post("/api/injector_contracts/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonInput))
        .andExpect(status().isOk());
  }

  // -- SCENARIO --
  @Test
  @DisplayName(
      "Test to validate existence of the 'Creation Scenario' endpoint and validate ScenarioInput")
  public void testCreateScenario_ValidInput() throws Exception {
    // -- PREPARE --
    String jsonInput =
        "{"
            + "\"scenario_name\": \"Test Scenario\", "
            + "\"scenario_subtitle\": \"Test Subtitle\", "
            + "\"scenario_description\": \"This is a test scenario.\", "
            + "\"scenario_category\": \"Incident Response\", "
            + "\"scenario_main_focus\": \"incident-response\", "
            + "\"scenario_severity\": \"high\", "
            + "\"scenario_external_reference\": \"test-ref-123\", "
            + "\"scenario_external_url\": \"https://example.com/dashboard/analyses/reports/test-ref-123\", "
            + "\"scenario_tags\": [\"tag-id-1\", \"tag-id-2\"]"
            + "}";

    // -- EXECUTE --
    mockMvc
        .perform(post("/api/scenarios").contentType(MediaType.APPLICATION_JSON).content(jsonInput))
        .andExpect(status().isOk());
  }

  // -- INJECTS --
  @Test
  @DisplayName(
      "Test to validate existence of 'Add Inject to Scenario' endpoint and validate InjectInput")
  public void testCreateInjectForScenario_ValidInput_Additional_param() throws Exception {
    // -- PREPARE --
    String jsonInput =
        "{"
            + "\"inject_title\": \"Valid Title\", "
            + "\"inject_type\": \"openbas_email\", "
            + "\"inject_injector_contract\": \"contract-id\", "
            + "\"inject_content\": null, "
            + "\"inject_depends_duration\": 100, "
            + "\"inject_tags\": [\"Tag1\"], "
            + "\"inject_enabled\": false, "
            + "\"inject_additional_param\": \"additional_param\""
            + "}";

    // -- EXECUTE --
    mockMvc
        .perform(
            post("/api/scenarios/{scenarioId}/injects", "scenario-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonInput))
        .andExpect(status().isOk());
  }

  // -- LAST EXERCISE BY EXTERNAL ID --
  @Test
  @DisplayName("Test to validate existence of the Get Last Exercise Result endpoint")
  public void testGetLatestExerciseByExternalReference_Success() throws Exception {
    // -- PREPARE --
    String externalReferenceId = "valid-id";

    // -- EXECUTE --
    mockMvc
        .perform(
            get("/api/opencti/v1/exercises/latest/{externalReferenceId}", externalReferenceId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }
}
