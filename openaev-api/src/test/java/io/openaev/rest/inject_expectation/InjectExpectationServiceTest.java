package io.openaev.rest.inject_expectation;

import static io.openaev.expectation.ExpectationPropertiesConfig.DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME;
import static io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegration.OPENAEV_INJECTOR_ID;
import static io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegration.OPENAEV_INJECTOR_NAME;
import static io.openaev.utils.fixtures.ExpectationFixture.createDetectionExpectations;
import static io.openaev.utils.fixtures.ExpectationFixture.createPreventionExpectations;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.model.Expectation;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
class InjectExpectationServiceTest extends IntegrationTest {

  private static final String INJECTION_NAME = "AMSI Bypass - AMSI InitFailed";
  private static final String INJECTOR_TYPE = "openaev_implant";

  // Saved entities for test setup
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private EntityManager entityManager;

  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private CollectorComposer collectorComposer;

  @Autowired private InjectExpectationService injectExpectationService;

  private static Injector savedInjector;
  private static InjectorContract savedInjectorContract;
  private static Asset savedAsset;

  @BeforeAll
  void beforeAll() throws JsonProcessingException {
    InjectorContract injectorContract =
        InjectorContractFixture.createInjectorContract(Map.of("en", INJECTION_NAME));
    savedInjector =
        injectorRepository.save(
            InjectorFixture.createInjector(
                OPENAEV_INJECTOR_ID, OPENAEV_INJECTOR_NAME, INJECTOR_TYPE));
    injectorContract.setInjector(savedInjector);

    savedInjectorContract = injectorContractRepository.save(injectorContract);
    savedAsset = assetRepository.save(AssetFixture.createDefaultAsset("asset name"));
    collectorComposer.forCollector(CollectorFixture.createDefaultCollector("FAKE")).persist();
  }

  @AfterAll
  void afterAll() {
    assetRepository.deleteAll();
  }

  @AfterEach
  void afterEach() {
    injectExpectationRepository.deleteAll();
    injectRepository.deleteAll();
    assetGroupRepository.deleteAll();
    agentRepository.deleteAll();
  }

  private Inject saveInject(InjectorContract injectorContract) {
    Inject inject =
        InjectFixture.createTechnicalInject(injectorContract, INJECTION_NAME, savedAsset);
    return injectRepository.save(inject);
  }

  private ExecutableInject createExecutableInject(
      Inject savedInject, List<AssetGroup> assetGroups) {
    return new ExecutableInject(
        false, true, savedInject, emptyList(), List.of(savedAsset), assetGroups, emptyList());
  }

  private Agent createAgent(String external01) {
    Agent agent = AgentFixture.createAgent(savedAsset, external01);
    return this.agentRepository.save(agent);
  }

  private AssetGroup createAssetGroup(String name) {
    AssetGroup assetGroup = AssetGroupFixture.createAssetGroupWithAssets(name, List.of(savedAsset));
    return assetGroupRepository.save(assetGroup);
  }

  @Test
  @DisplayName(
      "Expectations type prevention and detection should be created for agent linked to asset")
  void expectationsForAssetLinkedToAgent() {
    // -- PREPARE --
    Agent savedAgent = createAgent("external01");
    Inject savedInject = saveInject(savedInjectorContract);
    ExecutableInject executableInject = createExecutableInject(savedInject, emptyList());
    List<Expectation> detectionExpectations =
        createDetectionExpectations(
            List.of(savedAgent), savedAsset, null, DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> preventionExpectations =
        createPreventionExpectations(
            List.of(savedAgent), savedAsset, null, DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> expectations =
        java.util.stream.Stream.concat(
                detectionExpectations.stream(), preventionExpectations.stream())
            .toList();

    // -- EXECUTE --
    injectExpectationService.buildAndSaveInjectExpectations(executableInject, expectations);

    // -- ASSERT --
    assertEquals(4, injectExpectationRepository.findAll().spliterator().getExactSizeIfKnown());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAsset(savedInject.getId(), savedAsset.getId())
            .size());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAgent(savedInject.getId(), savedAgent.getId())
            .size());
  }

  @Test
  @DisplayName(
      "Expectations should be created for agent linked to asset who is part of an asset group")
  void expectationsForAssetGroupLinkedToAgent() {
    // -- PREPARE --
    Agent savedAgent = createAgent("external01");
    AssetGroup savedAssetGroup = createAssetGroup("asset group name");
    Inject savedInject = saveInject(savedInjectorContract);
    ExecutableInject executableInject =
        createExecutableInject(savedInject, List.of(savedAssetGroup));
    List<Expectation> detectionExpectations =
        createDetectionExpectations(
            List.of(savedAgent),
            savedAsset,
            savedAssetGroup,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> preventionExpectations =
        createPreventionExpectations(
            List.of(savedAgent),
            savedAsset,
            savedAssetGroup,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> expectations =
        java.util.stream.Stream.concat(
                detectionExpectations.stream(), preventionExpectations.stream())
            .toList();

    // -- EXECUTE --
    injectExpectationService.buildAndSaveInjectExpectations(executableInject, expectations);

    // -- ASSERT --
    assertEquals(6, injectExpectationRepository.findAll().spliterator().getExactSizeIfKnown());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAsset(savedInject.getId(), savedAsset.getId())
            .size());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAssetGroup(savedInject.getId(), savedAssetGroup.getId())
            .size());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAgent(savedInject.getId(), savedAgent.getId())
            .size());
  }

  @Test
  @DisplayName("Expectations should be created for asset with multiple agents")
  void expectationsForAssetWithMultipleAgents() {
    // -- PREPARE --
    Agent savedAgent = createAgent("external01");
    Agent savedAgent1 = createAgent("external02");
    Inject savedInject = saveInject(savedInjectorContract);
    ExecutableInject executableInject = createExecutableInject(savedInject, emptyList());
    List<Expectation> detectionExpectations =
        createDetectionExpectations(
            List.of(savedAgent, savedAgent1),
            savedAsset,
            null,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> preventionExpectations =
        createPreventionExpectations(
            List.of(savedAgent, savedAgent1),
            savedAsset,
            null,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> expectations =
        java.util.stream.Stream.concat(
                detectionExpectations.stream(), preventionExpectations.stream())
            .toList();

    // -- EXECUTE --
    injectExpectationService.buildAndSaveInjectExpectations(executableInject, expectations);

    // -- ASSERT --
    assertEquals(6, injectExpectationRepository.findAll().spliterator().getExactSizeIfKnown());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAsset(savedInject.getId(), savedAsset.getId())
            .size());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAgent(savedInject.getId(), savedAgent.getId())
            .size());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
            .size());
  }

  @Test
  @DisplayName("Expectations should be created for asset group with multiple agents")
  void expectationsForAssetGroupWithMultipleAgents() {
    // -- PREPARE --
    Agent savedAgent = createAgent("external01");
    Agent savedAgent1 = createAgent("external02");
    AssetGroup savedAssetGroup = createAssetGroup("assetGroup name");
    Inject savedInject = saveInject(savedInjectorContract);
    ExecutableInject executableInject =
        createExecutableInject(savedInject, List.of(savedAssetGroup));

    List<Expectation> detectionExpectations =
        createDetectionExpectations(
            List.of(savedAgent, savedAgent1),
            savedAsset,
            savedAssetGroup,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> preventionExpectations =
        createPreventionExpectations(
            List.of(savedAgent, savedAgent1),
            savedAsset,
            savedAssetGroup,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> expectations =
        java.util.stream.Stream.concat(
                detectionExpectations.stream(), preventionExpectations.stream())
            .toList();

    // -- EXECUTE --
    injectExpectationService.buildAndSaveInjectExpectations(executableInject, expectations);

    // -- ASSERT --
    assertEquals(8, injectExpectationRepository.findAll().spliterator().getExactSizeIfKnown());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAsset(savedInject.getId(), savedAsset.getId())
            .size());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAssetGroup(savedInject.getId(), savedAssetGroup.getId())
            .size());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAgent(savedInject.getId(), savedAgent.getId())
            .size());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
            .size());
  }
}
