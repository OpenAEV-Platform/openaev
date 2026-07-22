package io.openaev.rest.inject_expectation;

import static io.openaev.expectation.ExpectationPropertiesConfig.DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME;
import static io.openaev.utils.fixtures.ExpectationFixture.createDetectionExpectations;
import static io.openaev.utils.fixtures.ExpectationFixture.createPreventionExpectations;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.Expectation;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
class InjectExpectationServiceTest extends IntegrationTest {

  private static final String INJECTION_NAME = "AMSI Bypass - AMSI InitFailed";

  // Saved entities for test setup
  @Autowired private InjectorFixture injectorFixture;

  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private CollectorComposer collectorComposer;

  @Autowired private InjectExpectationService injectExpectationService;

  @Autowired private jakarta.persistence.EntityManager entityManager;

  private static Injector savedInjector;
  private static InjectorContract savedInjectorContract;
  private static Asset savedAsset;
  private static Collector savedCollector;

  @BeforeAll
  void beforeAll() throws JsonProcessingException {
    InjectorContract injectorContract =
        InjectorContractFixture.createInjectorContract(Map.of("en", INJECTION_NAME));

    savedInjector = injectorFixture.getWellKnownOaevImplantInjector();
    injectorContract.addInjector(savedInjector);

    savedInjectorContract = injectorContractRepository.save(injectorContract);
    savedInjector.linkContract(savedInjectorContract);
    injectorRepository.save(savedInjector);
    savedAsset = assetRepository.save(AssetFixture.createDefaultAsset("asset name"));
    savedCollector =
        collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector("FAKE"))
            .persist()
            .get();
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

  @Test
  @DisplayName(
      "Bulk compute should apply all agent results and propagate scores to asset and asset group parents")
  // Transactional like the production callers (bulkUpdateInjectExpectation API path and the
  // expiration manager's computeExpectations), which keep a session open during propagation
  @org.springframework.transaction.annotation.Transactional
  void bulkComputeTechnicalExpectationsShouldUpdateAgentsAndPropagateToParents() {
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
    injectExpectationService.buildAndSaveInjectExpectations(
        executableInject, detectionExpectations);
    // Detach everything so the service works on freshly loaded entities (incl. the inject's
    // expectations collection), exactly like the production collector callback path
    entityManager.flush();
    entityManager.clear();

    List<TechnicalInjectExpectation> agentExpectations =
        Stream.concat(
                injectExpectationRepository
                    .findAllByInjectAndAgent(savedInject.getId(), savedAgent.getId())
                    .stream(),
                injectExpectationRepository
                    .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
                    .stream())
            .filter(expectation -> expectation instanceof TechnicalInjectExpectation)
            .map(expectation -> (TechnicalInjectExpectation) expectation)
            .toList();
    assertEquals(2, agentExpectations.size());
    Map<String, InjectExpectationUpdateInput> inputsById =
        agentExpectations.stream()
            .collect(
                Collectors.toMap(
                    BaseInjectExpectation::getId,
                    expectation ->
                        InjectExpectationUpdateInput.builder()
                            .collectorId(savedCollector.getId())
                            .result("Detected")
                            .isSuccess(true)
                            .build()));

    // -- EXECUTE --
    injectExpectationService.bulkComputeTechnicalExpectations(
        agentExpectations, inputsById, savedCollector, false);
    // Assert against the persisted state, not the first-level cache
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    // Agent level: every expectation carries the collector result and a success score, exactly as
    // the per-item computeTechnicalExpectation path would have produced
    List<BaseInjectExpectation> updatedAgentExpectations =
        Stream.concat(
                injectExpectationRepository
                    .findAllByInjectAndAgent(savedInject.getId(), savedAgent.getId())
                    .stream(),
                injectExpectationRepository
                    .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
                    .stream())
            .toList();
    assertEquals(2, updatedAgentExpectations.size());
    updatedAgentExpectations.forEach(
        expectation -> {
          assertEquals(expectation.getExpectedScore(), expectation.getScore());
          assertEquals(1, expectation.getResults().size());
          assertEquals(savedCollector.getId(), expectation.getResults().getFirst().getSourceId());
        });

    // Parent propagation: the asset and asset group expectations are recomputed once per distinct
    // parent and end up successful since all their children succeeded
    Map<String, List<TechnicalInjectExpectation>> parents =
        Stream.concat(
                injectExpectationRepository
                    .findAllByInjectAndAsset(savedInject.getId(), savedAsset.getId())
                    .stream(),
                injectExpectationRepository
                    .findAllByInjectAndAssetGroup(savedInject.getId(), savedAssetGroup.getId())
                    .stream())
            .filter(TechnicalInjectExpectation.class::isInstance)
            .map(TechnicalInjectExpectation.class::cast)
            .collect(
                Collectors.groupingBy(
                    expectation ->
                        expectation.getAssetGroup() != null && expectation.getAsset() == null
                            ? "assetGroup"
                            : "asset",
                    Collectors.mapping(Function.identity(), Collectors.toList())));
    assertEquals(1, parents.get("asset").size());
    assertEquals(1, parents.get("assetGroup").size());
    assertEquals(
        parents.get("asset").getFirst().getExpectedScore(),
        parents.get("asset").getFirst().getScore());
    assertEquals(
        parents.get("assetGroup").getFirst().getExpectedScore(),
        parents.get("assetGroup").getFirst().getScore());
  }
}
