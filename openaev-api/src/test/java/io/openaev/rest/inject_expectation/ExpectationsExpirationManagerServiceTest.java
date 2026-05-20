package io.openaev.rest.inject_expectation;

import static io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegration.OPENAEV_INJECTOR_ID;
import static io.openaev.utils.fixtures.ExpectationFixture.*;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.collectors.expectations_expiration_manager.ExpectationsExpirationManagerJob;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.model.Expectation;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
public class ExpectationsExpirationManagerServiceTest extends IntegrationTest {

  private static final String INJECTION_NAME = "AMSI Bypass - AMSI InitFailed";

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
  @Autowired private ExpectationsExpirationManagerJob expectationsExpirationManagerJob;

  // Saved entities for test setup
  private Injector savedInjector;
  private InjectorContract savedInjectorContract;
  private AssetGroup savedAssetGroup;
  private Endpoint savedEndpoint;
  private Agent savedAgent1;
  private Agent savedAgent2;
  private Inject savedInject;

  @BeforeEach
  void beforeEach() throws Exception {
    // Register the builtin collector for the test tenant (builtins are only registered
    // for tenants that exist at startup, not for the test tenant created by @WithMockUser)
    expectationsExpirationManagerJob.registerForTenant();

    // Use the builtin injector if already registered, otherwise create it
    savedInjector =
        injectorRepository
            .findById(OPENAEV_INJECTOR_ID)
            .orElseGet(
                () ->
                    injectorRepository.save(
                        InjectorFixture.createInjector(
                            OPENAEV_INJECTOR_ID, "OpenAEV Implant", "openaev_implant")));

    InjectorContract injectorContract;
    try {
      injectorContract =
          InjectorContractFixture.createInjectorContract(java.util.Map.of("en", INJECTION_NAME));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    injectorContract.addInjector(savedInjector);
    savedInjectorContract = injectorContractRepository.save(injectorContract);
    savedInjector.getContracts().add(savedInjectorContract);
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
  }

  @Nested
  @DisplayName("Update injectExpectations with expectationsExpirationManagerService")
  class ComputeExpectationsWithExpectationExpiredManagerService {

    @Test
    @DisplayName("All injectExpectations are expired")
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
      detectionExpectations.add(
          createTechnicalDetectionExpectationForAsset(savedEndpoint, null, EXPIRATION_TIME_1_s));
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
                  .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                  .result("result")
                  .sourceAssetId(UUID.randomUUID().toString())
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
                        .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                        .result("result")
                        .sourceAssetId(UUID.randomUUID().toString())
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

    @Test
    @DisplayName("Vulnerability expectation with an agent gets expired")
    void vulnerableExpectationIsExpired() {
      // -- PREPARE --
      // Build and save an expectation for an asset and one agent
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation expectation =
          createTechnicalVulnerabilityExpectationForAgent(
              savedAgent1, savedEndpoint, null, EXPIRATION_TIME_1_s, null);

      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, List.of(expectation));

      // -- VERIFY --
      List<InjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations();

      // -- ASSERT --
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(100.0, injectExpectations.getFirst().getScore());
      assertEquals(
          InjectExpectation.EXPECTATION_STATUS.SUCCESS,
          injectExpectations.getFirst().getResponse());
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
