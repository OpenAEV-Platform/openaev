package io.openaev.rest;

import static io.openaev.expectation.ExpectationPropertiesConfig.DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME;
import static io.openaev.expectation.ExpectationType.DETECTION;
import static io.openaev.expectation.ExpectationType.PREVENTION;
import static io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegration.OPENAEV_INJECTOR_NAME;
import static io.openaev.rest.expectation.ExpectationApi.EXPECTATIONS_URI;
import static io.openaev.rest.expectation.ExpectationApi.INJECTS_EXPECTATIONS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.ExpectationFixture.*;
import static io.openaev.utils.fixtures.InjectExpectationFixture.getInjectExpectationUpdateInput;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.Expectation;
import io.openaev.integration.impl.injectors.challenge.ChallengeInjectorIntegrationFactory;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.rest.inject.form.InjectExpectationBulkUpdateInput;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@WithMockUser(isAdmin = true)
class ExpectationApiTest extends IntegrationTest {

  // API Endpoints
  private static final String INJECTION_NAME = "AMSI Bypass - AMSI InitFailed";
  private static final String INJECTOR_TYPE = "openaev_implant_test";

  @Autowired private MockMvc mvc;
  @Autowired private EntityManager em;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private CollectorTypeRepository collectorTypeRepository;
  @Autowired private CollectorRepository collectorRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private InjectExpectationService injectExpectationService;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private ChallengeInjectorIntegrationFactory challengeInjectorIntegrationFactory;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  // Saved entities for test setup
  private Injector savedInjector;
  private InjectorContract savedInjectorContract;
  private AssetGroup savedAssetGroup;
  private Endpoint savedEndpoint;
  private Agent savedAgent1;
  private Agent savedAgent2;
  private Inject savedInject;
  private Collector savedCollector;
  private Collector savedCollector2;

  @BeforeEach
  void setUp() throws JsonProcessingException {
    InjectorContract injectorContract =
        InjectorContractFixture.createInjectorContract(Map.of("en", INJECTION_NAME));
    injectorContract.setCustom(true);
    savedInjector =
        injectorRepository.save(
            InjectorFixture.createInjector(
                UUID.randomUUID().toString(), OPENAEV_INJECTOR_NAME + "-test", INJECTOR_TYPE));
    injectorContract.addInjector(savedInjector);
    savedInjectorContract = injectorContractRepository.save(injectorContract);
    em.flush();
    savedInjector.linkContract(savedInjectorContract);
    injectorRepository.save(savedInjector);

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
    CollectorType collectorType1 = new CollectorType(UUID.randomUUID().toString());
    CollectorType collectorType2 = new CollectorType(UUID.randomUUID().toString());

    collectorTypeRepository.save(collectorType1);
    collectorTypeRepository.save(collectorType2);

    Collector collector = new Collector();
    collector.setId(UUID.randomUUID().toString());
    collector.setName("collector-name");
    collector.setType(collectorType1.getName());
    collector.setCollectorType(collectorType1);
    collector.setExternal(true);
    savedCollector = collectorRepository.save(collector);

    Collector collector2 = new Collector();
    collector2.setId(UUID.randomUUID().toString());
    collector2.setName("collector-2-name");
    collector2.setType(collectorType2.getName());
    collector2.setCollectorType(collectorType2);
    collector2.setExternal(true);
    savedCollector2 = collectorRepository.save(collector2);
  }

  @Nested
  @Transactional
  @WithMockUser(isAdmin = true)
  @DisplayName("Update and delete inject expectation results from UI")
  class ResultInjectExpectation {

    /**
     * Validates adding and deleting results from the UI for a single agent and checks score
     * propagation at agent, asset, and asset group levels.
     */
    @Test
    @DisplayName("Add results on inject expectation from UI on one agent")
    @WithMockUser(isAdmin = true)
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
      em.flush();
      em.clear();

      // -- EXECUTE --

      // Retrieve Agent expectation
      List<BaseInjectExpectation> injectExpectations =
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
    @WithMockUser(isAdmin = true)
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
      em.flush();
      em.clear();

      // -- EXECUTE --

      // Retrieve Agent expectation
      List<BaseInjectExpectation> injectExpectations =
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

    /**
     * Validates that deleting a result at asset level (asset with agents) cascades the deletion of
     * that source's result to every agent expectation of the asset, then recomputes the asset and
     * asset group scores from the remaining agent results.
     */
    @Test
    @DisplayName("Delete result on asset expectation from UI cascades to all agents")
    @WithMockUser(isAdmin = true)
    void deleteResultOnAssetWithAgentsFromUI() throws Exception {
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
      em.flush();
      em.clear();

      // Fill the same source's result on both agents, like a security platform collector does
      ExpectationUpdateInput expectationUpdateInput = getExpectationUpdateInput("fake-1", 100.0);
      callUpdateInjectExpectationFromUI(
          injectExpectationRepository
              .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
              .getFirst(),
          expectationUpdateInput);
      callUpdateInjectExpectationFromUI(
          injectExpectationRepository
              .findAllByInjectAndAgent(savedInject.getId(), savedAgent2.getId())
              .getFirst(),
          expectationUpdateInput);

      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(100.0, getScore(assetExpectations));

      // -- EXECUTE --

      // Delete the source's result directly at ASSET level
      callDeleteInjectExpectationFromUI(assetExpectations.getFirst(), expectationUpdateInput);

      // -- ASSERT --
      // Agent 1: result removed, score reset
      List<BaseInjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertTrue(
          injectExpectations.getFirst().getResults().stream()
              .noneMatch(r -> "fake-1".equals(r.getSourceId())));
      assertEquals(null, getScore(injectExpectations));
      // Agent 2: result removed, score reset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertTrue(
          injectExpectations.getFirst().getResults().stream()
              .noneMatch(r -> "fake-1".equals(r.getSourceId())));
      assertEquals(null, getScore(injectExpectations));
      // Asset: score recomputed from agents
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, getScore(injectExpectations));
      // Asset Group: score recomputed from assets
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, getScore(injectExpectations));
    }
  }

  @Nested
  @Transactional
  @WithMockUser(isAdmin = true)
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
      em.flush();
      em.clear();

      // Update one expectation from one agent with source collector-id
      List<BaseInjectExpectation> injectExpectations =
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
                      .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                      .sourceAssetId(UUID.randomUUID().toString())
                      .score(50.0)
                      .build()));

      injectExpectationRepository.save(injectExpectations.getFirst());

      // -- EXECUTE --
      String response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + "/assets/" + savedCollector.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(1, ((List<?>) JsonPath.read(response, "$")).size());
      assertEquals(savedAgent1.getId(), JsonPath.read(response, "$.[0].inject_expectation_agent"));
    }

    /**
     * Regression test: signatures live in a dedicated table behind a LAZY collection since #5151.
     * The collector polling endpoint must serialize them as a real array, not null, otherwise
     * collectors (Defender, Sentinel, Tanium...) can never match any expectation.
     */
    @Test
    @DisplayName("Serialize signatures for collector polling endpoint")
    void getInjectExpectationsForSourceReturnsSignatures() throws Exception {
      // -- PREPARE --
      ExecutableInject executableInject = newExecutableInjectWithTargets(false);
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1),
              savedEndpoint,
              savedAssetGroup,
              DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);
      em.flush();
      em.clear();

      // Attach a signature to the agent expectation, as SignatureOutputProcessor does after the
      // implant reports its execution traces.
      BaseInjectExpectation agentExpectation =
          injectExpectationRepository
              .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
              .getFirst();
      agentExpectation
          .getSignatures()
          .add(
              new InjectExpectationSignature(
                  agentExpectation, "process_name", "obfuscated.exe", java.time.Instant.now()));
      injectExpectationRepository.save(agentExpectation);
      // Detach everything so the endpoint reloads entities with an UNINITIALIZED lazy signatures
      // collection, exactly like a real collector poll on a fresh session.
      em.flush();
      em.clear();

      // -- EXECUTE --
      String response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + "/assets/" + savedCollector.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT: the agent expectation carries its signatures, and no expectation in the
      // payload has null signatures or null results --
      List<Map<String, Object>> expectations = JsonPath.read(response, "$");
      assertTrue(expectations.size() >= 1);
      for (Map<String, Object> expectation : expectations) {
        Assertions.assertNotNull(
            expectation.get("inject_expectation_signatures"),
            "inject_expectation_signatures must never be serialized as null");
        Assertions.assertNotNull(
            expectation.get("inject_expectation_results"),
            "inject_expectation_results must never be serialized as null");
      }
      List<Map<String, String>> signatures =
          JsonPath.read(
              response,
              "$.[?(@.inject_expectation_agent == '"
                  + savedAgent1.getId()
                  + "')].inject_expectation_signatures[*]");
      assertEquals(1, signatures.size());
      assertEquals("process_name", signatures.getFirst().get("type"));
      assertEquals("obfuscated.exe", signatures.getFirst().get("value"));
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
      em.flush();
      em.clear();

      // -- EXECUTE --
      String response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + "/prevention/" + savedCollector.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
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
      List<BaseInjectExpectation> injectExpectations =
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
                      .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                      .sourceAssetId(UUID.randomUUID().toString())
                      .result("result")
                      .score(80.0)
                      .build()));

      injectExpectationRepository.save(injectExpectations.get(0));

      // -- EXECUTE --
      response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + "/prevention/" + savedCollector.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
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
      em.flush();
      em.clear();

      // -- EXECUTE --
      String response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + "/detection/" + savedCollector.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
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
      List<BaseInjectExpectation> injectExpectations =
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
                      .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                      .sourceAssetId(UUID.randomUUID().toString())
                      .result("result")
                      .score(90.0)
                      .build()));

      injectExpectationRepository.save(injectExpectations.get(0));

      // -- EXECUTE --
      response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + "/detection/" + savedCollector.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
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
      em.flush();
      em.clear();

      // -- EXECUTE --

      // Retrieve Agent expectation
      List<BaseInjectExpectation> injectExpectationsAgent =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());

      // Add Success result to Agent expectation
      InjectExpectationUpdateInput expectationUpdateInput =
          getInjectExpectationUpdateInput(savedCollector.getId(), DETECTION.successLabel, true);
      callUpdateInjectExpectation(injectExpectationsAgent.getFirst(), expectationUpdateInput);

      // -- ASSERT --
      // Agent Expectation
      List<BaseInjectExpectation> injectExpectations =
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
      List<BaseInjectExpectation> injectExpectationsAgent1 =
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
    @WithMockUser(isAdmin = true)
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
      em.flush();
      em.clear();

      // -- EXECUTE --

      // Retrieve Agent expectation
      List<BaseInjectExpectation> injectExpectations =
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
      em.flush();
      em.clear();

      // Fetch BaseInjectExpectation created for agent 1
      List<BaseInjectExpectation> injectExpectationsAgent1 =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      InjectExpectationUpdateInput expectationUpdateInputAgent1 =
          getInjectExpectationUpdateInput(savedCollector.getId(), "Detected", true);
      // Fetch BaseInjectExpectation created for agent 2
      List<BaseInjectExpectation> injectExpectationsAgent2 =
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
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      // Agent Expectation
      List<BaseInjectExpectation> injectExpectations =
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

  @Nested
  @Transactional
  @WithMockUser(isAdmin = true)
  @DisplayName("AI defense feed scoping and parent expectation protection")
  class AiDefenseFeedAndParentProtection {

    /**
     * Regression test (parent asset wrongly "Not Detected" while its agent was green): the AI
     * defense feed must never hand out PARENT asset expectations - rows with agent children whose
     * score is derived from the agents. An LLM firewall collector receiving such a parent would
     * later expire it with a direct failed result, clobbering the agents' green verdict.
     */
    @Test
    @DisplayName("AI defense feed excludes endpoint parents with agent children")
    void aiDefenseFeedExcludesParentsWithAgentChildren() throws Exception {
      // -- PREPARE --
      Collector llmCollector = createCollectorWithSecurityPlatform("LLM_FIREWALL");
      ExecutableInject executableInject = newExecutableInjectWithTargets(true);
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1),
              savedEndpoint,
              savedAssetGroup,
              DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);
      em.flush();
      em.clear();

      // -- EXECUTE --
      String response = callGetAiDefenseExpectations(llmCollector);

      // -- ASSERT: the asset-level PARENT (agent child exists) must not be handed out --
      assertEquals(0, ((List<?>) JsonPath.read(response, "$")).size());
    }

    /**
     * Regression test: the AI defense feed must respect the expectation's expected security
     * platform types - an expectation restricted to EDR must never reach an LLM firewall collector,
     * even when it is a genuine agentless leaf.
     */
    @Test
    @DisplayName("AI defense feed respects expected security platform types")
    void aiDefenseFeedRespectsExpectedSecurityPlatforms() throws Exception {
      // -- PREPARE --
      Collector llmCollector = createCollectorWithSecurityPlatform("LLM_FIREWALL");
      Endpoint agentlessEndpoint =
          endpointRepository.save(EndpointFixture.createEndpoint("agentless-endpoint"));
      ExecutableInject executableInject = newExecutableInjectWithTargets(false);
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              emptyList(), agentlessEndpoint, null, DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);
      em.flush();
      em.clear();

      // Empty expected platforms means "any security platform": the leaf is handed out
      String response = callGetAiDefenseExpectations(llmCollector);
      assertEquals(1, ((List<?>) JsonPath.read(response, "$")).size());

      // Restrict the expectation to EDR: it must no longer reach an LLM firewall collector
      TechnicalInjectExpectation leaf =
          (TechnicalInjectExpectation)
              injectExpectationRepository
                  .findAllByInjectAndAsset(savedInject.getId(), agentlessEndpoint.getId())
                  .getFirst();
      leaf.setExpectedSecurityPlatforms(List.of(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR));
      injectExpectationRepository.save(leaf);
      em.flush();
      em.clear();

      response = callGetAiDefenseExpectations(llmCollector);
      assertEquals(0, ((List<?>) JsonPath.read(response, "$")).size());

      // Restrict it to LLM_FIREWALL: the LLM firewall collector receives it again
      leaf =
          (TechnicalInjectExpectation)
              injectExpectationRepository
                  .findAllByInjectAndAsset(savedInject.getId(), agentlessEndpoint.getId())
                  .getFirst();
      leaf.setExpectedSecurityPlatforms(
          List.of(SecurityPlatform.SECURITY_PLATFORM_TYPE.LLM_FIREWALL));
      injectExpectationRepository.save(leaf);
      em.flush();
      em.clear();

      response = callGetAiDefenseExpectations(llmCollector);
      assertEquals(1, ((List<?>) JsonPath.read(response, "$")).size());
    }

    /**
     * Regression test: a collector writing a failed result DIRECTLY on an asset-level parent
     * expectation (asset with agent children) must not clobber the score derived from the agents.
     * The green verdict rolled up from the agents stays, the direct result is only recorded.
     */
    @Test
    @DisplayName("Direct collector failure cannot clobber a parent whose agents are green")
    void directFailureOnParentKeepsChildrenVerdict() throws Exception {
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
      em.flush();
      em.clear();

      // Agent answered green by the EDR collector: asset and asset group roll up to green
      List<BaseInjectExpectation> agentExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      callUpdateInjectExpectation(
          agentExpectations.getFirst(),
          getInjectExpectationUpdateInput(savedCollector.getId(), DETECTION.successLabel, true));
      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(100.0, getScore(assetExpectations));

      // -- EXECUTE: another collector writes a failure DIRECTLY on the asset-level parent --
      callUpdateInjectExpectation(
          assetExpectations.getFirst(),
          getInjectExpectationUpdateInput(savedCollector2.getId(), DETECTION.failureLabel, false));

      // -- ASSERT --
      // The direct result is recorded on the parent row...
      assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, getResultScoreForCollector(assetExpectations, savedCollector2).get());
      // ...but the children-derived verdict is untouched
      assertEquals(100.0, getScore(assetExpectations));
      List<BaseInjectExpectation> assetGroupExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(100.0, getScore(assetGroupExpectations));
    }

    /**
     * Regression test: a direct failure on a parent whose agents are still unanswered must keep the
     * parent PENDING (null score) - the agents' security platform may still answer green.
     */
    @Test
    @DisplayName("Direct collector failure keeps parent pending while agents are unanswered")
    void directFailureOnParentKeepsPendingChildren() throws Exception {
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
      em.flush();
      em.clear();

      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());

      // -- EXECUTE: a collector writes a failure DIRECTLY on the asset-level parent --
      callUpdateInjectExpectation(
          assetExpectations.getFirst(),
          getInjectExpectationUpdateInput(savedCollector2.getId(), DETECTION.failureLabel, false));

      // -- ASSERT: the parent stays pending, waiting for its agents --
      assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, getScore(assetExpectations));
      List<BaseInjectExpectation> assetGroupExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, getScore(assetGroupExpectations));
    }

    private Collector createCollectorWithSecurityPlatform(String platformType) {
      SecurityPlatform securityPlatform =
          securityPlatformRepository.save(
              SecurityPlatformFixture.createDefault(
                  platformType + "-platform-" + UUID.randomUUID(), platformType));
      CollectorType collectorType = new CollectorType(UUID.randomUUID().toString());
      collectorTypeRepository.save(collectorType);
      Collector collector = new Collector();
      collector.setId(UUID.randomUUID().toString());
      collector.setName(platformType + "-collector");
      collector.setType(collectorType.getName());
      collector.setCollectorType(collectorType);
      collector.setExternal(true);
      collector.setSecurityPlatform(securityPlatform);
      return collectorRepository.save(collector);
    }

    private String callGetAiDefenseExpectations(Collector collector) throws Exception {
      return mvc.perform(
              get(INJECTS_EXPECTATIONS_URI + "/ai/" + collector.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();
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

  private Double getScore(@NotNull final List<? extends BaseInjectExpectation> injectExpectations) {
    return injectExpectations.getFirst().getScore();
  }

  private Optional<Double> getResultScoreForCollector(
      @NotNull final List<? extends BaseInjectExpectation> injectExpectations,
      @NotNull final Collector collector) {
    return injectExpectations.getFirst().getResults().stream()
        .filter(result -> result.getSourceId().equals(collector.getId()))
        .map(InjectExpectationResult::getScore)
        .findFirst();
  }

  // MVC CALL

  private void callUpdateInjectExpectation(
      @NotNull final BaseInjectExpectation baseInjectExpectation,
      @NotNull final InjectExpectationUpdateInput expectationUpdateInput)
      throws Exception {
    mvc.perform(
            put(INJECTS_EXPECTATIONS_URI + "/" + baseInjectExpectation.getId())
                .content(asJsonString(expectationUpdateInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  private void callUpdateInjectExpectationFromUI(
      @NotNull final BaseInjectExpectation baseInjectExpectation,
      @NotNull final ExpectationUpdateInput expectationUpdateInput)
      throws Exception {
    mvc.perform(
            put(EXPECTATIONS_URI + "/" + baseInjectExpectation.getId())
                .content(asJsonString(expectationUpdateInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  private void callDeleteInjectExpectationFromUI(
      @NotNull final BaseInjectExpectation baseInjectExpectation,
      @NotNull final ExpectationUpdateInput expectationUpdateInput)
      throws Exception {
    mvc.perform(
            put(EXPECTATIONS_URI
                    + "/"
                    + baseInjectExpectation.getId()
                    + "/"
                    + expectationUpdateInput.getSourceId()
                    + "/delete")
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }
}
