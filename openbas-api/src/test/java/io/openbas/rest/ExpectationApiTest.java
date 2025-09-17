package io.openbas.rest;

import static io.openbas.expectation.ExpectationPropertiesConfig.DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME;
import static io.openbas.expectation.ExpectationType.DETECTION;
import static io.openbas.expectation.ExpectationType.PREVENTION;
import static io.openbas.injectors.openbas.OpenBASInjector.OPENBAS_INJECTOR_ID;
import static io.openbas.injectors.openbas.OpenBASInjector.OPENBAS_INJECTOR_NAME;
import static io.openbas.rest.expectation.ExpectationApi.EXPECTATIONS_URI;
import static io.openbas.rest.expectation.ExpectationApi.INJECTS_EXPECTATIONS_URI;
import static io.openbas.utils.JsonUtils.asJsonString;
import static io.openbas.utils.fixtures.ExpectationFixture.*;
import static io.openbas.utils.fixtures.InjectExpectationFixture.getInjectExpectationUpdateInput;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.repository.*;
import io.openbas.execution.ExecutableInject;
import io.openbas.model.Expectation;
import io.openbas.rest.exercise.form.ExpectationUpdateInput;
import io.openbas.rest.inject.form.InjectExpectationBulkUpdateInput;
import io.openbas.rest.inject.form.InjectExpectationUpdateInput;
import io.openbas.service.InjectExpectationService;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.mockUser.WithMockAdminUser;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
class ExpectationApiTest extends IntegrationTest {

  // API Endpoints
  private static final String INJECTION_NAME = "AMSI Bypass - AMSI InitFailed";
  private static final String INJECTOR_TYPE = "openbas_implant";

  @Autowired private MockMvc mvc;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private CollectorRepository collectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private InjectExpectationService injectExpectationService;

  // Saved entities for test setup
  private static Injector savedInjector;
  private static InjectorContract savedInjectorContract;
  private static AssetGroup savedAssetGroup;
  private static Endpoint savedEndpoint;
  private static Agent savedAgent1;
  private static Agent savedAgent2;
  private static Inject savedInject;
  private static Collector savedCollector;
  private static Collector savedCollector2;

  @BeforeAll
  void beforeAll() throws JsonProcessingException {
    InjectorContract injectorContract =
        InjectorContractFixture.createInjectorContract(Map.of("en", INJECTION_NAME));
    savedInjector =
        injectorRepository.save(
            InjectorFixture.createInjector(
                OPENBAS_INJECTOR_ID, OPENBAS_INJECTOR_NAME, INJECTOR_TYPE));
    injectorContract.setInjector(savedInjector);
    savedInjectorContract = injectorContractRepository.save(injectorContract);

    // -- Targets --
    savedEndpoint = endpointRepository.save(EndpointFixture.createEndpoint());
    savedAgent1 = agentRepository.save(AgentFixture.createAgent(savedEndpoint, "external01"));
    savedAgent2 = agentRepository.save(AgentFixture.createAgent(savedEndpoint, "external02"));
    savedAssetGroup =
        assetGroupRepository.save(
            AssetGroupFixture.createAssetGroupWithAssets(
                "asset group name", List.of(savedEndpoint)));

    // -- Inject --
    savedInject =
        injectRepository.save(
            InjectFixture.createTechnicalInjectWithAssetGroup(
                savedInjectorContract, INJECTION_NAME, savedAssetGroup));

    // -- Collector --
    Collector collector = new Collector();
    collector.setId(UUID.randomUUID().toString());
    collector.setName("collector-name");
    collector.setType(UUID.randomUUID().toString());
    collector.setExternal(true);
    savedCollector = collectorRepository.save(collector);

    Collector collector2 = new Collector();
    collector2.setId(UUID.randomUUID().toString());
    collector2.setName("collector-2-name");
    collector2.setType(UUID.randomUUID().toString());
    collector2.setExternal(true);
    savedCollector2 = collectorRepository.save(collector2);
  }

  @AfterAll
  void afterAll() {
    agentRepository.deleteAll();
    injectRepository.deleteAll();
    endpointRepository.deleteAll();
    collectorRepository.deleteById(savedCollector.getId());
    collectorRepository.deleteById(savedCollector2.getId());
  }

  @AfterEach
  void afterEach() {
    injectExpectationRepository.deleteAll();
  }

  @Nested
  @WithMockAdminUser
  @DisplayName("Update and delete inject expectation results from UI")
  class ResultInjectExpectation {

    /**
     * Validates adding and deleting results from the UI for a single agent and checks score
     * propagation at agent, asset, and asset group levels.
     */
    @Test
    @DisplayName("Add results on inject expectation from UI on one agent")
    @WithMockAdminUser
    void addResultsOnOneAgentFromUI() throws Exception {
      // -- PREPARE --
      ExecutableInject executableInject = newExecutableInjectWithTargets(true);
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1),
              savedEndpoint,
              savedAssetGroup,
              DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      // -- EXECUTE --

      // Retrieve Agent expectation
      List<InjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());

      // Add Success result to Agent expectation
      ExpectationUpdateInput expectationUpdateInput = getExpectationUpdateInput("fake-1", 100.0);
      callUpdateInjectExpectationFromUI(injectExpectations.getFirst(), expectationUpdateInput);

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(100.0, getScore(injectExpectations));
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(100.0, getScore(injectExpectations));
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(100.0, getScore(injectExpectations));

      // -- EXECUTE --

      // Retrieve Agent expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());

      // Add Failure result to Agent expectation
      expectationUpdateInput = getExpectationUpdateInput("fake-2", 0.0);
      callUpdateInjectExpectationFromUI(injectExpectations.getFirst(), expectationUpdateInput);

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(100.0, getScore(injectExpectations));
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(100.0, getScore(injectExpectations));
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(100.0, getScore(injectExpectations));

      // -- EXECUTE --

      // Retrieve Agent expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());

      // Remove Error result to Agent expectation
      expectationUpdateInput = getExpectationUpdateInput("fake-2", 0.0);
      callDeleteInjectExpectationFromUI(injectExpectations.getFirst(), expectationUpdateInput);

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(100.0, getScore(injectExpectations));
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(100.0, getScore(injectExpectations));
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(100.0, getScore(injectExpectations));
    }

    /**
     * Validates adding results from the UI on two agents, ensuring propagation and behavior when a
     * mix of success/failure occurs.
     */
    @Test
    @DisplayName("Add results on inject expectation from UI on two agents")
    @WithMockAdminUser
    void addResultsOnTwoAgentFromUI() throws Exception {
      // -- PREPARE --
      ExecutableInject executableInject = newExecutableInjectWithTargets(true);
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);

      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      // -- EXECUTE --

      // Retrieve Agent expectation
      List<InjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());

      // Add Success result to Agent 1 expectation
      ExpectationUpdateInput expectationUpdateInput = getExpectationUpdateInput("fake-1", 100.0);
      callUpdateInjectExpectationFromUI(injectExpectations.getFirst(), expectationUpdateInput);

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(100.0, getScore(injectExpectations));
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, getScore(injectExpectations));
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, getScore(injectExpectations));

      // -- EXECUTE --

      // Retrieve Agent 2 expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());

      // Add Failure result to Agent 2 expectation
      expectationUpdateInput = getExpectationUpdateInput("fake-2", 0.0);
      callUpdateInjectExpectationFromUI(injectExpectations.getFirst(), expectationUpdateInput);

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(0.0, getScore(injectExpectations));
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, getScore(injectExpectations));
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(0.0, getScore(injectExpectations));

      // -- EXECUTE --

      // Retrieve Agent 2 expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());

      // Remove Failure result to Agent 2 expectation
      expectationUpdateInput = getExpectationUpdateInput("fake-2", 0.0);
      callDeleteInjectExpectationFromUI(injectExpectations.getFirst(), expectationUpdateInput);

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(null, getScore(injectExpectations));
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, getScore(injectExpectations));
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, getScore(injectExpectations));

      // -- EXECUTE --

      // Retrieve Agent expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());

      // Add Success result to Agent 2 expectation
      expectationUpdateInput = getExpectationUpdateInput("fake-2", 100.0);
      callUpdateInjectExpectationFromUI(injectExpectations.getFirst(), expectationUpdateInput);

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(100.0, getScore(injectExpectations));
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(100.0, getScore(injectExpectations));
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(100.00, getScore(injectExpectations));
    }
  }

  @Nested
  @WithMockAdminUser
  @DisplayName("Fetch and update InjectExpectations from collectors")
  class ProcessInjectExpectationsForCollectors {

    /**
     * Ensures expectations are retrieved for a given source (collector), after pre-filling one
     * expectation with a result from that collector.
     */
    @Test
    @DisplayName("Get Inject Expectations for a Specific Source")
    void getInjectExpectationsForSource() throws Exception {
      // -- PREPARE --
      // Build and save expectations
      ExecutableInject executableInject = newExecutableInjectWithTargets(false);
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1),
              savedEndpoint,
              savedAssetGroup,
              DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);

      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      // Update one expectation from one agent with source collector-id
      List<InjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());

      injectExpectations
          .getFirst()
          .setResults(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId(savedCollector.getId())
                      .sourceName(savedCollector.getName())
                      .sourceType(savedCollector.getType())
                      .score(50.0)
                      .build()));

      injectExpectationRepository.save(injectExpectations.getFirst());

      // -- EXECUTE --
      String response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + "/assets/" + savedCollector.getId())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(1, ((List<?>) JsonPath.read(response, "$")).size());
      assertEquals(savedAgent1.getId(), JsonPath.read(response, "$.[0].inject_expectation_agent"));
    }

    /**
     * Lists PREVENTION expectations for a source, then fills one expectation (agent2) so that the
     * second query returns only remaining unfilled (agent1).
     */
    @Test
    @DisplayName("Get Prevention Inject Expectations for a Specific Source")
    void getInjectPreventionExpectationsForSource() throws Exception {
      // -- PREPARE --
      // Build and save expectations for an asset with 2 agents
      ExecutableInject executableInject =
          new ExecutableInject(
              false,
              true,
              savedInject,
              emptyList(),
              List.of(savedEndpoint),
              emptyList(),
              emptyList());
      List<Expectation> preventionExpectations =
          createPreventionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);

      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, preventionExpectations);

      // -- EXECUTE --
      String response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + "/prevention/" + savedCollector.getId())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(2, ((List<?>) JsonPath.read(response, "$")).size());
      assertEquals(
          savedEndpoint.getId(), JsonPath.read(response, "$.[0].inject_expectation_asset"));
      assertEquals(PREVENTION.name(), JsonPath.read(response, "$.[0].inject_expectation_type"));

      // -- PREPARE --
      // Update one expectation from one agent with source collector-id then this expectation is
      // filled and it should return just one
      List<InjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());

      injectExpectations
          .get(0)
          .setResults(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId(savedCollector.getId())
                      .sourceName(savedCollector.getName())
                      .sourceType(savedCollector.getType())
                      .result("result")
                      .score(80.0)
                      .build()));

      injectExpectationRepository.save(injectExpectations.get(0));

      // -- EXECUTE --
      response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + "/prevention/" + savedCollector.getId())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(1, ((List<?>) JsonPath.read(response, "$")).size());
      assertEquals(
          savedEndpoint.getId(), JsonPath.read(response, "$.[0].inject_expectation_asset"));
      assertEquals(savedAgent1.getId(), JsonPath.read(response, "$.[0].inject_expectation_agent"));
    }

    /**
     * Lists DETECTION expectations for a source, then fills one expectation (agent2) so that the
     * second query returns only remaining unfilled (agent1).
     */
    @Test
    @DisplayName("Get Detection Inject Expectations for a Specific Source")
    void getInjectDetectionExpectationsForSource() throws Exception {
      // -- PREPARE --
      // Build and save expectations for an asset with 2 agents
      ExecutableInject executableInject =
          new ExecutableInject(
              false,
              true,
              savedInject,
              emptyList(),
              List.of(savedEndpoint),
              emptyList(),
              emptyList());
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);

      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      // -- EXECUTE --
      String response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + "/detection/" + savedCollector.getId())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(2, ((List<?>) JsonPath.read(response, "$")).size());
      assertEquals(
          savedEndpoint.getId(), JsonPath.read(response, "$.[0].inject_expectation_asset"));
      assertEquals(DETECTION.name(), JsonPath.read(response, "$.[0].inject_expectation_type"));

      // -- PREPARE --
      // Update one expectation from one agent with source collector-id then it should return one
      // expectation
      List<InjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());

      injectExpectations
          .get(0)
          .setResults(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId(savedCollector.getId())
                      .sourceName(savedCollector.getName())
                      .sourceType(savedCollector.getType())
                      .result("result")
                      .score(90.0)
                      .build()));

      injectExpectationRepository.save(injectExpectations.get(0));

      // -- EXECUTE --
      response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + "/detection/" + savedCollector.getId())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(1, ((List<?>) JsonPath.read(response, "$")).size());
      assertEquals(
          savedEndpoint.getId(), JsonPath.read(response, "$.[0].inject_expectation_asset"));
      assertEquals(savedAgent1.getId(), JsonPath.read(response, "$.[0].inject_expectation_agent"));
    }

    /**
     * Verifies propagation rules with two agents: - Do not update endpoint/asset-group levels until
     * all agent-level expectations are filled. - Once a failure exists for another agent,
     * propagation results in 0 at higher levels.
     */
    @Test
    @DisplayName("Add results on inject expectation from one collectors on two agents")
    void updateInjectExpectationWithTwoSuccess() throws Exception {
      // -- PREPARE --
      // Build and save expectations for an asset with 2 agents
      ExecutableInject executableInject = newExecutableInjectWithTargets(true);
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);

      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      // -- EXECUTE --

      // Retrieve Agent expectation
      List<InjectExpectation> injectExpectationsAgent =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());

      // Add Success result to Agent expectation
      InjectExpectationUpdateInput expectationUpdateInput =
          getInjectExpectationUpdateInput(savedCollector.getId(), DETECTION.successLabel, true);
      callUpdateInjectExpectation(injectExpectationsAgent.getFirst(), expectationUpdateInput);

      // -- ASSERT --
      // Agent Expectation
      List<InjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(100.0, getResultScoreForCollector(injectExpectations, savedCollector).get());
      // Asset Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertTrue(getResultScoreForCollector(injectExpectations, savedCollector).isEmpty());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertTrue(getResultScoreForCollector(injectExpectations, savedCollector).isEmpty());

      // -- EXECUTE --

      // Retrieve Agent1 expectation
      List<InjectExpectation> injectExpectationsAgent1 =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());

      // Add Failure result to Agent1 expectation
      expectationUpdateInput =
          getInjectExpectationUpdateInput(savedCollector.getId(), DETECTION.failureLabel, false);
      callUpdateInjectExpectation(injectExpectationsAgent1.getFirst(), expectationUpdateInput);

      // -- ASSERT --
      // Agent1 Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(0.0, getResultScoreForCollector(injectExpectations, savedCollector).get());
      // Asset Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, getScore(injectExpectations));
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(0.0, getScore(injectExpectations));
    }

    /**
     * Verifies combining results from two different collectors on a single agent, and checks
     * propagation to asset and asset-group levels for each collector.
     */
    @Test
    @WithMockAdminUser
    @DisplayName("Add results on inject expectation from two collectors on one agent")
    void updateInjectExpectationFromTwoCollectors() throws Exception {
      // -- PREPARE --
      // Inject with 1 Agent, 1 Asset & 1 Asset Group
      ExecutableInject executableInject = newExecutableInjectWithTargets(true);
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1),
              savedEndpoint,
              savedAssetGroup,
              DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);

      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      // -- EXECUTE --

      // Retrieve Agent expectation
      List<InjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());

      // Add Success result to Agent expectation
      InjectExpectationUpdateInput expectationUpdateInput =
          getInjectExpectationUpdateInput(savedCollector.getId(), DETECTION.successLabel, true);
      callUpdateInjectExpectation(injectExpectations.getFirst(), expectationUpdateInput);

      // Add Failure result to Agent expectation
      InjectExpectationUpdateInput expectationUpdateInput2 =
          getInjectExpectationUpdateInput(savedCollector2.getId(), DETECTION.failureLabel, false);
      callUpdateInjectExpectation(injectExpectations.getFirst(), expectationUpdateInput2);

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(100.0, getResultScoreForCollector(injectExpectations, savedCollector).get());
      assertEquals(0.0, getResultScoreForCollector(injectExpectations, savedCollector2).get());
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(100.0, getScore(injectExpectations));
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(100.0, getScore(injectExpectations));
    }

    /**
     * Verifies bulk update behavior for expectations: - Sends two updates (one success, one
     * failure) for the same collector. - Ensures no premature propagation to asset/asset-group when
     * all agent expectations are not fully satisfied.
     */
    @Test
    @DisplayName("Bulk update Inject expectation from collector with success")
    void bulkUpdateInjectExpectationWithTwoSuccess() throws Exception {
      // -- PREPARE --
      // Build and save expectations for an asset with 2 agents
      ExecutableInject executableInject = newExecutableInjectWithTargets(true);
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);

      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      // Fetch injectExpectation created for agent 1
      List<InjectExpectation> injectExpectationsAgent1 =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      InjectExpectationUpdateInput expectationUpdateInputAgent1 =
          getInjectExpectationUpdateInput(savedCollector.getId(), "Detected", true);
      // Fetch injectExpectation created for agent 2
      List<InjectExpectation> injectExpectationsAgent2 =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      InjectExpectationUpdateInput expectationUpdateInputAgent2 =
          getInjectExpectationUpdateInput(savedCollector.getId(), "Not detected", false);

      InjectExpectationBulkUpdateInput inputs =
          new InjectExpectationBulkUpdateInput(
              Map.of(
                  injectExpectationsAgent1.getFirst().getId(), expectationUpdateInputAgent1,
                  injectExpectationsAgent2.getFirst().getId(), expectationUpdateInputAgent2));

      // -- EXECUTE --
      mvc.perform(
              put(INJECTS_EXPECTATIONS_URI + "/bulk")
                  .content(asJsonString(inputs))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      // Agent Expectation
      List<InjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(100.0, getResultScoreForCollector(injectExpectations, savedCollector).get());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(0.0, getResultScoreForCollector(injectExpectations, savedCollector).get());
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, getScore(injectExpectations));
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(0.0, getScore(injectExpectations));
    }
  }

  // -- PRIVATE HELPERS --

  private ExecutableInject newExecutableInjectWithTargets(boolean includeAssetGroup) {
    return new ExecutableInject(
        false,
        true,
        savedInject,
        emptyList(),
        List.of(savedEndpoint),
        includeAssetGroup ? List.of(savedAssetGroup) : emptyList(),
        emptyList());
  }

  private Double getScore(@NotNull final List<InjectExpectation> injectExpectations) {
    return injectExpectations.getFirst().getScore();
  }

  private Optional<Double> getResultScoreForCollector(
      @NotNull final List<InjectExpectation> injectExpectations,
      @NotNull final Collector collector) {
    return injectExpectations.getFirst().getResults().stream()
        .filter(result -> result.getSourceId().equals(collector.getId()))
        .map(InjectExpectationResult::getScore)
        .findFirst();
  }

  // MVC CALL

  private void callUpdateInjectExpectation(
      @NotNull final InjectExpectation injectExpectation,
      @NotNull final InjectExpectationUpdateInput expectationUpdateInput)
      throws Exception {
    mvc.perform(
            put(INJECTS_EXPECTATIONS_URI + "/" + injectExpectation.getId())
                .content(asJsonString(expectationUpdateInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
  }

  private void callUpdateInjectExpectationFromUI(
      @NotNull final InjectExpectation injectExpectation,
      @NotNull final ExpectationUpdateInput expectationUpdateInput)
      throws Exception {
    mvc.perform(
            put(EXPECTATIONS_URI + "/" + injectExpectation.getId())
                .content(asJsonString(expectationUpdateInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
  }

  private void callDeleteInjectExpectationFromUI(
      @NotNull final InjectExpectation injectExpectation,
      @NotNull final ExpectationUpdateInput expectationUpdateInput)
      throws Exception {
    mvc.perform(
            put(
                EXPECTATIONS_URI
                    + "/"
                    + injectExpectation.getId()
                    + "/"
                    + expectationUpdateInput.getSourceId()
                    + "/delete"))
        .andExpect(status().is2xxSuccessful());
  }
}
