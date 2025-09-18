package io.openbas.rest.inject_expectation;

import static io.openbas.injectors.openbas.OpenBASInjector.OPENBAS_INJECTOR_ID;
import static io.openbas.injectors.openbas.OpenBASInjector.OPENBAS_INJECTOR_NAME;
import static io.openbas.utils.fixtures.ExpectationFixture.createDetectionExpectations;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openbas.IntegrationTest;
import io.openbas.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openbas.database.model.*;
import io.openbas.database.repository.*;
import io.openbas.execution.ExecutableInject;
import io.openbas.model.Expectation;
import io.openbas.service.InjectExpectationService;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class ExpectationsExpirationManagerServiceTest extends IntegrationTest {

  private static final String INJECTION_NAME = "AMSI Bypass - AMSI InitFailed";
  private static final String INJECTOR_TYPE = "openbas_implant";

  public static final long EXPIRATION_TIME_1_s = 1L;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private InjectExpectationService injectExpectationService;
  @Autowired private ExpectationsExpirationManagerService expectationsExpirationManagerService;

  // Saved entities for test setup
  private static Injector savedInjector;
  private static InjectorContract savedInjectorContract;
  private static AssetGroup savedAssetGroup;
  private static Endpoint savedEndpoint;
  private static Agent savedAgent1;
  private static Agent savedAgent2;
  private static Inject savedInject;

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
  }

  @AfterAll
  void afterAll() {
    agentRepository.deleteAll();
    injectRepository.deleteAll();
    endpointRepository.deleteAll();
  }

  @AfterEach
  void afterEach() {
    injectExpectationRepository.deleteAll();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Update injectExpectations with expectationsExpirationManagerService")
  class ComputeExpectationsWithExpectationExpiredManagerService {

    @Test
    @DisplayName("All injectExpectations are expired")
    @WithMockUser(isAdmin = true)
    void allExpectationAreExpired() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      // -- VERIFY --
      // Agent Expectation
      List<InjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations();

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("One injectExpectations is already filled")
    @WithMockUser(isAdmin = true)
    void OneExpectationIsAlreadyFilled() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      // Update one expectation from one agent with source collector-id
      List<InjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());

      InjectExpectation ie = injectExpectations.getFirst();
      ie.setResults(
          List.of(
              InjectExpectationResult.builder()
                  .sourceId("collector-id")
                  .sourceName("collector-name")
                  .sourceType("collector-type")
                  .result("result")
                  .score(50.0)
                  .build()));
      ie.setScore(50.0);

      injectExpectationRepository.save(ie);

      // -- VERIFY --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(50.0, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations();

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(50.0, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("The agent expectations are already filled")
    @WithMockUser(isAdmin = true)
    void agentExpectationsAreAlreadyFilled() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents

      ExecutableInject executableInject = newExecutableInjectWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      // Update agent expectations with source collector-id
      List<InjectExpectation> injectExpectations =
          List.of(
              injectExpectationRepository
                  .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
                  .getFirst(),
              injectExpectationRepository
                  .findAllByInjectAndAgent(savedInject.getId(), savedAgent2.getId())
                  .getFirst());

      injectExpectations.forEach(
          injectExpectation -> {
            injectExpectation.setResults(
                List.of(
                    InjectExpectationResult.builder()
                        .sourceId("collector-id")
                        .sourceName("collector-name")
                        .sourceType("collector-type")
                        .result("result")
                        .score(100.0)
                        .build()));
            injectExpectation.setScore(100.0);
          });

      injectExpectationRepository.saveAll(injectExpectations);

      // -- VERIFY --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(100.0, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(100.0, injectExpectations.getFirst().getScore());
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations();

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(100.0, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(100.0, injectExpectations.getFirst().getScore());
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("Asset expectations without agent expectation linked")
    @WithMockUser(isAdmin = true)
    void assetExpectationWithoutAgentExpectationsLinked() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      // Delete agent inject expectations to test behavior of assets without agents
      List<InjectExpectation> injectExpectations =
          List.of(
              injectExpectationRepository
                  .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
                  .getFirst(),
              injectExpectationRepository
                  .findAllByInjectAndAgent(savedInject.getId(), savedAgent2.getId())
                  .getFirst());

      List<String> ids = injectExpectations.stream().map(InjectExpectation::getId).toList();

      injectExpectationRepository.deleteAllById(ids);

      // -- VERIFY --
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations();

      // -- ASSERT --
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
    }
  }

  // -- PRIVATE HELPERS --

  private ExecutableInject newExecutableInjectWithTargets() {
    return new ExecutableInject(
        false,
        true,
        savedInject,
        emptyList(),
        List.of(savedEndpoint),
        List.of(savedAssetGroup),
        emptyList());
  }
}
