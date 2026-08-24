package io.openaev.service.expectation;

import static io.openaev.utils.ExpectationSignatureUtils.EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME;
import static io.openaev.utils.ExpectationSignatureUtils.EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
class AbstractTechnicalBehaviorTest extends IntegrationTest {

  @Autowired private DetectionBehavior detectionBehavior;
  @Autowired private InjectExpectationService injectExpectationService;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;

  @BeforeEach
  void setUp() {
    endpointComposer.reset();
    agentComposer.reset();
    injectComposer.reset();
    exerciseComposer.reset();
    assetGroupComposer.reset();
    injectorContractComposer.reset();
    collectorComposer.reset();
    securityPlatformComposer.reset();
  }

  // -- Shared helpers --

  private Exercise persistDefaultExercise() {
    return exerciseComposer
        .forExercise(ExerciseFixture.createDefaultCrisisExercise())
        .persist()
        .get();
  }

  private DetectionInjectExpectation createTemplate(Inject inject) {
    DetectionInjectExpectation template = new DetectionInjectExpectation();
    template.setExpectedScore(100.0);
    template.setExpirationTime(21600L);
    template.setInject(inject);
    return template;
  }

  private void persistTwoCollector(String name, String type) {
    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(name))
        .withSecurityPlatform(
            securityPlatformComposer.forSecurityPlatform(
                SecurityPlatformFixture.createDefault(name, type)))
        .persist();
  }

  private List<BaseInjectExpectation> actAndGetSavedExpectations(
      ExecutableInject executableInject, DetectionInjectExpectation template, String source) {
    detectionBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
        executableInject, template, source, null);
    entityManager.flush();
    return injectExpectationRepository.findAllByInjectId(
        executableInject.getInjection().getInject().getId());
  }

  private static long countAgentExpectations(List<BaseInjectExpectation> expectations) {
    return expectations.stream()
        .filter(e -> ((TechnicalInjectExpectation) e).getAgent() != null)
        .count();
  }

  private static long countAssetOnlyExpectations(List<BaseInjectExpectation> expectations) {
    return expectations.stream()
        .filter(
            e ->
                ((TechnicalInjectExpectation) e).getAgent() == null
                    && ((TechnicalInjectExpectation) e).getAsset() != null)
        .count();
  }

  private static long countAssetGroupOnlyExpectations(List<BaseInjectExpectation> expectations) {
    return expectations.stream()
        .filter(
            e ->
                ((TechnicalInjectExpectation) e).getAgent() == null
                    && ((TechnicalInjectExpectation) e).getAsset() == null
                    && ((TechnicalInjectExpectation) e).getAssetGroup() != null)
        .count();
  }

  private static List<BaseInjectExpectation> filterByAssetGroup(
      List<BaseInjectExpectation> expectations, AssetGroup assetGroup) {
    return expectations.stream()
        .filter(e -> ((TechnicalInjectExpectation) e).getAssetGroup() == assetGroup)
        .toList();
  }

  @Nested
  @DisplayName("initializeAndSaveInjectExpectationsFromExecutableInject")
  class InitializeAndSaveInjectExpectation {

    @Test
    @DisplayName("given one asset with inactive agent should not create expectation")
    void given_one_asset_with_inactive_agent_should_not_create_expectation() {
      // Arrange
      Endpoint endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createInactiveAgent()))
              .persist()
              .get();

      Exercise exercise = persistDefaultExercise();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointComposer.forEndpoint(endpoint))
              .withExercise(exerciseComposer.forExercise(exercise))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of());

      // Act & Assert
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "oaev");
      assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("given one asset attached to two agents should create three inject expectations")
    void given_one_asset_attached_to_two_agents_should_create_three_inject_expectations() {
      // Arrange
      Endpoint endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession()))
              .persist()
              .get();

      Exercise exercise = persistDefaultExercise();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointComposer.forEndpoint(endpoint))
              .withExercise(exerciseComposer.forExercise(exercise))
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of());

      // Act
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "oaev");

      // Assert — 1 asset expectation + 2 agent expectations = 3
      assertThat(saved).hasSize(3);
      assertThat(countAgentExpectations(saved)).isEqualTo(2);
      assertThat(countAssetOnlyExpectations(saved)).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "given one inject executed by executor with asset agentless should not create expectation")
    void given_inject_executed_by_executor_with_asset_agentless_should_not_create_expectation() {
      // Arrange
      Endpoint endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();

      Exercise exercise = persistDefaultExercise();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointComposer.forEndpoint(endpoint))
              .withExercise(exerciseComposer.forExercise(exercise))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of());

      // Act & Assert
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "oaev");
      assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName(
        "given one inject executed by injector with asset agentless should create one expectation")
    void given_inject_executed_by_injector_with_asset_agentless_should_create_expectation() {
      // Arrange
      Endpoint endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();

      Exercise exercise = persistDefaultExercise();
      Inject defaultInject = InjectFixture.getDefaultInject();
      Injector injector = InjectorFixture.createDefaultInjector("nmap");
      injector.setPayloads(false);
      entityManager.persist(injector);
      defaultInject.setInjector(injector);
      Inject inject =
          injectComposer
              .forInject(defaultInject)
              .withEndpoint(endpointComposer.forEndpoint(endpoint))
              .withExercise(exerciseComposer.forExercise(exercise))
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of());

      // Act & Assert
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "nmap");
      assertThat(saved).hasSize(1);
    }

    @Test
    @DisplayName(
        "given two asset groups each attached to the same asset with one active agent should create six inject expectations")
    void
        given_two_asset_groups_each_attached_to_same_asset_with_one_active_agent_should_create_six_inject_expectations() {
      // Arrange
      Endpoint endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
              .persist()
              .get();

      AssetGroup assetGroupA =
          assetGroupComposer
              .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("Group A"))
              .withAsset(endpointComposer.forEndpoint(endpoint))
              .persist()
              .get();
      AssetGroup assetGroupB =
          assetGroupComposer
              .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("Group B"))
              .withAsset(endpointComposer.forEndpoint(endpoint))
              .persist()
              .get();

      Exercise exercise = persistDefaultExercise();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withAssetGroup(assetGroupComposer.forAssetGroup(assetGroupA))
              .withAssetGroup(assetGroupComposer.forAssetGroup(assetGroupB))
              .withExercise(exerciseComposer.forExercise(exercise))
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false,
              false,
              inject,
              List.of(),
              List.of(),
              List.of(assetGroupA, assetGroupB),
              List.of());

      // Act
      List<BaseInjectExpectation> allSaved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "oaev");

      // Assert — per asset group: 1 agent + 1 asset + 1 assetGroup = 3, × 2 groups = 6
      assertThat(allSaved).hasSize(6);

      List<BaseInjectExpectation> groupAExpectations = filterByAssetGroup(allSaved, assetGroupA);
      assertThat(groupAExpectations).hasSize(3);
      assertThat(countAgentExpectations(groupAExpectations)).isEqualTo(1);
      assertThat(countAssetOnlyExpectations(groupAExpectations)).isEqualTo(1);
      assertThat(countAssetGroupOnlyExpectations(groupAExpectations)).isEqualTo(1);

      List<BaseInjectExpectation> groupBExpectations = filterByAssetGroup(allSaved, assetGroupB);
      assertThat(countAgentExpectations(groupBExpectations)).isEqualTo(1);
      assertThat(countAssetOnlyExpectations(groupBExpectations)).isEqualTo(1);
      assertThat(countAssetGroupOnlyExpectations(groupBExpectations)).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "given one asset with one active agent plus one asset group linked to same asset should create five expectations")
    void
        given_one_asset_with_one_active_agent_plus_one_asset_group_linked_to_same_asset_should_create_five_expectations() {
      // Arrange
      Endpoint endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
              .persist()
              .get();

      AssetGroup assetGroup =
          assetGroupComposer
              .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("Group"))
              .withAsset(endpointComposer.forEndpoint(endpoint))
              .persist()
              .get();

      Exercise exercise = persistDefaultExercise();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointComposer.forEndpoint(endpoint))
              .withAssetGroup(assetGroupComposer.forAssetGroup(assetGroup))
              .withExercise(exerciseComposer.forExercise(exercise))
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(assetGroup), List.of());

      // Act
      List<BaseInjectExpectation> allSaved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "oaev");

      // Assert — direct: 1 agent + 1 asset = 2, group: 1 agent + 1 asset + 1 assetGroup = 3 → 5
      assertThat(allSaved).hasSize(5);

      List<BaseInjectExpectation> directExpectations =
          allSaved.stream()
              .filter(e -> ((TechnicalInjectExpectation) e).getAssetGroup() == null)
              .toList();
      assertThat(directExpectations).hasSize(2);
      assertThat(countAssetOnlyExpectations(directExpectations)).isEqualTo(1);
      assertThat(countAgentExpectations(directExpectations)).isEqualTo(1);
      List<BaseInjectExpectation> groupExpectations = filterByAssetGroup(allSaved, assetGroup);
      assertThat(groupExpectations).hasSize(3);
    }

    @Test
    @DisplayName(
        "given one asset with one agent, should create result and signature on agent level on each available collector")
    void
        given_one_asset_with_one_agent_should_create_result_and_signature_on_agent_level_for_each_collector() {
      // Arrange
      Endpoint endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
              .persist()
              .get();

      persistTwoCollector("collector-siem", "EDR");
      persistTwoCollector("collector-edr", "EDR");
      Exercise exercise = persistDefaultExercise();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointComposer.forEndpoint(endpoint))
              .withExercise(exerciseComposer.forExercise(exercise))
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of());

      // Act
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "oaev");

      // Assert — 1 agent expectation + 1 asset expectation = 2
      assertThat(saved).hasSize(2);

      // Agent-level expectation: results (one per collector) and signatures must be set
      List<TechnicalInjectExpectation> agentExpectations =
          saved.stream()
              .map(TechnicalInjectExpectation.class::cast)
              .filter(e -> e.getAgent() != null)
              .toList();
      assertThat(agentExpectations).hasSize(1);

      TechnicalInjectExpectation agentExpectation = agentExpectations.getFirst();
      assertThat(agentExpectation.getResults()).hasSize(2);
      assertThat(agentExpectation.getResults())
          .extracting(InjectExpectationResult::getSourceId)
          .containsExactlyInAnyOrder("collector-edr", "collector-siem");
      assertThat(agentExpectation.getSignatures()).isNotEmpty();

      // Asset-level (parent) expectation: no results, no signatures
      List<TechnicalInjectExpectation> assetExpectations =
          saved.stream()
              .map(TechnicalInjectExpectation.class::cast)
              .filter(e -> e.getAgent() == null && e.getAsset() != null)
              .toList();
      assertThat(assetExpectations).hasSize(1);

      TechnicalInjectExpectation assetExpectation = assetExpectations.getFirst();
      assertThat(assetExpectation.getResults()).isEmpty();
      assertThat(assetExpectation.getSignatures()).isEmpty();
    }

    @Test
    @DisplayName(
        "given one asset agentless, should create result and signature on asset level for each available collector")
    void
        given_one_asset_agentless_should_create_result_and_signature_on_asset_level_for_each_collector() {
      // Arrange
      Endpoint endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();

      persistTwoCollector("collector-siem", "EDR");
      persistTwoCollector("collector-edr", "EDR");

      Exercise exercise = persistDefaultExercise();

      Injector nonPayloadInjector = InjectorFixture.createDefaultInjector("nmap");
      nonPayloadInjector.setPayloads(false);
      Inject defaultInject = InjectFixture.getDefaultInject();
      defaultInject.setInjector(nonPayloadInjector);

      Inject inject =
          injectComposer
              .forInject(defaultInject)
              .withEndpoint(endpointComposer.forEndpoint(endpoint))
              .withExercise(exerciseComposer.forExercise(exercise))
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(nonPayloadInjector))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of());

      // Act
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "nmap");

      // Assert — 1 asset-level expectation (agentless, no agent expectation)
      assertThat(saved).hasSize(1);

      TechnicalInjectExpectation assetExpectation = (TechnicalInjectExpectation) saved.getFirst();
      assertThat(assetExpectation.getAsset()).isNotNull();
      assertThat(assetExpectation.getAgent()).isNull();

      // Results — one per collector, set on asset level
      assertThat(assetExpectation.getResults()).hasSize(2);
      assertThat(assetExpectation.getResults())
          .extracting(InjectExpectationResult::getSourceId)
          .containsExactlyInAnyOrder("collector-edr", "collector-siem");

      // Signatures — set on asset level
      assertThat(assetExpectation.getSignatures()).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Expectation signatures")
  class ExpectationSignatures {

    @Test
    @DisplayName(
        "given one asset with one agent should set parent process and source ip signatures on agent level")
    void
        given_one_asset_with_one_agent_should_set_parent_process_and_source_ip_signatures_on_agent_level() {
      // Arrange
      Endpoint endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
              .persist()
              .get();
      Agent agent = endpoint.getAgents().getFirst();

      persistTwoCollector("collector-edr", "EDR");
      Exercise exercise = persistDefaultExercise();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointComposer.forEndpoint(endpoint))
              .withExercise(exerciseComposer.forExercise(exercise))
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of());

      // Act
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "oaev");

      // Assert — agent-level signatures carry the parent process name and the source IP
      TechnicalInjectExpectation agentExpectation =
          saved.stream()
              .map(TechnicalInjectExpectation.class::cast)
              .filter(e -> e.getAgent() != null)
              .findFirst()
              .orElseThrow();

      assertThat(agentExpectation.getSignatures())
          .extracting(InjectExpectationSignature::getType, InjectExpectationSignature::getValue)
          .containsExactlyInAnyOrder(
              tuple(
                  EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME,
                  "oaev" + inject.getId() + "-agent-" + agent.getId()),
              tuple(EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS, "192.168.1.1"));
    }

    @Test
    @DisplayName("given one asset agentless should set source ip signature on asset level")
    void given_one_asset_agentless_should_set_source_ip_signature_on_asset_level() {
      // Arrange
      Endpoint endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();

      persistTwoCollector("collector-edr", "EDR");
      Exercise exercise = persistDefaultExercise();

      Injector nonPayloadInjector = InjectorFixture.createDefaultInjector("nmap");
      nonPayloadInjector.setPayloads(false);
      Inject defaultInject = InjectFixture.getDefaultInject();
      defaultInject.setInjector(nonPayloadInjector);

      Inject inject =
          injectComposer
              .forInject(defaultInject)
              .withEndpoint(endpointComposer.forEndpoint(endpoint))
              .withExercise(exerciseComposer.forExercise(exercise))
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(nonPayloadInjector))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of());

      // Act
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "nmap");

      // Assert — asset-level signature carries the source IP (no agent, so no parent process name)
      TechnicalInjectExpectation assetExpectation = (TechnicalInjectExpectation) saved.getFirst();

      assertThat(assetExpectation.getSignatures())
          .extracting(InjectExpectationSignature::getType, InjectExpectationSignature::getValue)
          .contains(tuple(EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS, "192.168.1.1"));
    }
  }

  @Nested
  @DisplayName("GetLeaves")
  class GetLeaves {

    @Test
    @DisplayName("given agent expectation should return this expectation")
    void given_agent_expectation_should_return_this_expectation() {
      // Arrange
      Endpoint endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
              .persist()
              .get();
      Agent agent = endpoint.getAgents().getFirst();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withExercise(exerciseComposer.forExercise(persistDefaultExercise()))
              .persist()
              .get();

      DetectionInjectExpectation agentExpectation =
          InjectExpectationFixture.createDetectionInjectExpectation(inject, agent);
      agentExpectation.setAsset(endpoint);

      DetectionInjectExpectation assetExpectation =
          InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
      assetExpectation.setAsset(endpoint);

      injectExpectationRepository.saveAll(List.of(agentExpectation, assetExpectation));
      entityManager.flush();
      entityManager.refresh(inject);

      // Act
      List<? extends BaseInjectExpectation> leaves = detectionBehavior.getLeaves(agentExpectation);

      // Assert
      assertThat(leaves).hasSize(1);
      assertThat(leaves.getFirst()).isEqualTo(agentExpectation);
    }

    @Test
    @DisplayName("given asset expectation should return agents expectations attached to this asset")
    void given_asset_expectation_should_return_agents_expectations() {
      // Arrange
      Endpoint endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession()))
              .persist()
              .get();
      Agent agent1 = endpoint.getAgents().get(0);
      Agent agent2 = endpoint.getAgents().get(1);

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withExercise(exerciseComposer.forExercise(persistDefaultExercise()))
              .persist()
              .get();

      DetectionInjectExpectation agentExpectation1 =
          InjectExpectationFixture.createDetectionInjectExpectation(inject, agent1);
      agentExpectation1.setAsset(endpoint);

      DetectionInjectExpectation agentExpectation2 =
          InjectExpectationFixture.createDetectionInjectExpectation(inject, agent2);
      agentExpectation2.setAsset(endpoint);

      DetectionInjectExpectation assetExpectation =
          InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
      assetExpectation.setAsset(endpoint);

      injectExpectationRepository.saveAll(
          List.of(agentExpectation1, agentExpectation2, assetExpectation));
      entityManager.flush();
      entityManager.refresh(inject);

      // Act
      List<? extends BaseInjectExpectation> leaves = detectionBehavior.getLeaves(assetExpectation);

      // Assert
      assertThat(leaves).hasSize(2);
      assertThat(leaves).allMatch(e -> ((TechnicalInjectExpectation) e).getAgent() != null);
    }

    @Test
    @DisplayName("given asset agentless expectation should return this expectation")
    void given_asset_agentless_expectation_should_return_this_expectation() {
      // Arrange
      Endpoint endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withExercise(exerciseComposer.forExercise(persistDefaultExercise()))
              .persist()
              .get();

      DetectionInjectExpectation assetExpectation =
          InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
      assetExpectation.setAsset(endpoint);

      injectExpectationRepository.save(assetExpectation);
      entityManager.flush();
      entityManager.refresh(inject);

      // Act
      List<? extends BaseInjectExpectation> leaves = detectionBehavior.getLeaves(assetExpectation);

      // Assert
      assertThat(leaves).hasSize(1);
      assertThat(leaves.getFirst()).isEqualTo(assetExpectation);
      assertThat(((TechnicalInjectExpectation) leaves.getFirst()).getAgent()).isNull();
    }

    @Test
    @DisplayName(
        "given asset-group expectation should return the agents expectation attached to the assets inside the group")
    void given_asset_group_expectation_should_return_agents_expectations_of_group() {
      // Arrange
      Endpoint endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
              .persist()
              .get();
      Agent agent = endpoint.getAgents().getFirst();

      AssetGroup assetGroup =
          assetGroupComposer
              .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("Group"))
              .withAsset(endpointComposer.forEndpoint(endpoint))
              .persist()
              .get();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withExercise(exerciseComposer.forExercise(persistDefaultExercise()))
              .persist()
              .get();

      DetectionInjectExpectation agentExpectation =
          InjectExpectationFixture.createDetectionInjectExpectation(inject, agent);
      agentExpectation.setAsset(endpoint);
      agentExpectation.setAssetGroup(assetGroup);

      DetectionInjectExpectation assetExpectation =
          InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
      assetExpectation.setAsset(endpoint);
      assetExpectation.setAssetGroup(assetGroup);

      DetectionInjectExpectation assetGroupExpectation =
          InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
      assetGroupExpectation.setAssetGroup(assetGroup);

      injectExpectationRepository.saveAll(
          List.of(agentExpectation, assetExpectation, assetGroupExpectation));
      entityManager.flush();
      entityManager.refresh(inject);

      // Act
      List<? extends BaseInjectExpectation> leaves =
          detectionBehavior.getLeaves(assetGroupExpectation);

      // Assert
      assertThat(leaves).hasSize(1);
      assertThat(leaves).allMatch(e -> ((TechnicalInjectExpectation) e).getAgent() != null);
    }
  }

  @Nested
  @DisplayName("UpdateInjectExpectationUsingBehaviors")
  class UpdateInjectExpectationUsingBehaviors {

    private static final String SOURCE_ID = "collector-1";

    // -- Shared hierarchy: 1 asset-group, assetA (2 agents), assetB (agentless) --

    private record Hierarchy(
        Inject inject,
        DetectionInjectExpectation agentA1,
        DetectionInjectExpectation agentA2,
        DetectionInjectExpectation assetA,
        DetectionInjectExpectation assetB,
        DetectionInjectExpectation assetGroup) {}

    private Hierarchy createHierarchy(boolean isGroup) {
      Endpoint endpointA =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession()))
              .persist()
              .get();
      Endpoint endpointB =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();

      AssetGroup group =
          assetGroupComposer
              .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("Group"))
              .withAsset(endpointComposer.forEndpoint(endpointA))
              .withAsset(endpointComposer.forEndpoint(endpointB))
              .persist()
              .get();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withExercise(exerciseComposer.forExercise(persistDefaultExercise()))
              .persist()
              .get();

      DetectionInjectExpectation agentA1Expectation =
          buildExpectation(inject, endpointA.getAgents().get(0), endpointA, group, isGroup);
      DetectionInjectExpectation agentA2Expectation =
          buildExpectation(inject, endpointA.getAgents().get(1), endpointA, group, isGroup);
      DetectionInjectExpectation assetAExpectation =
          buildExpectation(inject, null, endpointA, group, isGroup);
      DetectionInjectExpectation assetBExpectation =
          buildExpectation(inject, null, endpointB, group, isGroup);

      DetectionInjectExpectation assetGroupExp =
          buildExpectation(inject, null, null, group, isGroup);

      injectExpectationRepository.saveAll(
          List.of(
              agentA1Expectation,
              agentA2Expectation,
              assetAExpectation,
              assetBExpectation,
              assetGroupExp));
      entityManager.flush();
      entityManager.refresh(inject);

      return new Hierarchy(
          inject,
          agentA1Expectation,
          agentA2Expectation,
          assetAExpectation,
          assetBExpectation,
          assetGroupExp);
    }

    private DetectionInjectExpectation buildExpectation(
        Inject inject, Agent agent, Endpoint asset, AssetGroup group, boolean isGroup) {
      DetectionInjectExpectation exp =
          InjectExpectationFixture.createDetectionInjectExpectation(inject, agent);
      exp.setAsset(asset);
      exp.setAssetGroup(group);
      exp.setExpectationGroup(isGroup);
      if (agent != null) {
        exp.setResults(
            new java.util.ArrayList<>(
                List.of(
                    InjectExpectationResult.builder()
                        .sourceId(SOURCE_ID)
                        .result(null)
                        .score(null)
                        .build())));
      }
      return exp;
    }

    private void preScore(DetectionInjectExpectation exp, Double score) {
      if (score != null) {
        exp.setScore(score);
        if (exp.getAgent() != null) {
          exp.getResults().getFirst().setScore(score);
        }
        injectExpectationRepository.save(exp);
      }
    }

    private DetectionInjectExpectation resolveTarget(Hierarchy h, String target) {
      return switch (target) {
        case "agentA1" -> h.agentA1;
        case "agentA2" -> h.agentA2;
        case "assetA" -> h.assetA;
        case "assetB" -> h.assetB;
        case "assetGroup" -> h.assetGroup;
        default -> throw new IllegalArgumentException("Unknown target: " + target);
      };
    }

    private TechnicalInjectExpectation findById(List<BaseInjectExpectation> saved, String id) {
      return saved.stream()
          .map(TechnicalInjectExpectation.class::cast)
          .filter(e -> e.getId().equals(id))
          .findFirst()
          .orElseThrow();
    }

    // -- Leaf-level updates (agent or agentless asset) --

    @Nested
    @DisplayName("Leaf-level update")
    class LeafLevelUpdate {

      static Stream<Arguments> scenarios() {
        return Stream.of(
            // isGroup=false (all must validate)
            Arguments.of(
                false,
                null,
                null,
                null,
                null,
                "agentA1",
                100.0,
                null,
                null,
                "allMustValidate: one agent success, asset and group pending"),
            Arguments.of(
                false,
                100.0,
                null,
                null,
                null,
                "agentA2",
                100.0,
                100.0,
                null,
                "allMustValidate: both agents success, asset success, group pending"),
            Arguments.of(
                false,
                null,
                null,
                null,
                null,
                "agentA1",
                80.0,
                0.0,
                0.0,
                "allMustValidate: one agent failed, asset and group failed"),
            Arguments.of(
                false,
                100.0,
                100.0,
                100.0,
                null,
                "assetB",
                100.0,
                100.0,
                100.0,
                "allMustValidate: all leaves success, asset and group success"),
            // isGroup=true (at least one must validate)
            Arguments.of(
                true,
                null,
                null,
                null,
                null,
                "agentA1",
                100.0,
                100.0,
                100.0,
                "atLeastOne: one agent success, asset and group success"),
            Arguments.of(
                true,
                80.0,
                null,
                null,
                null,
                "agentA2",
                100.0,
                100.0,
                100.0,
                "atLeastOne: one failed one success, asset and group success"),
            Arguments.of(
                true,
                null,
                null,
                null,
                null,
                "agentA1",
                80.0,
                null,
                null,
                "atLeastOne: one agent failed, asset and group pending"),
            Arguments.of(
                true,
                80.0,
                80.0,
                0.0,
                null,
                "assetB",
                100.0,
                0.0,
                100.0,
                "atLeastOne: asset failed, agentless success, group success"));
      }

      @ParameterizedTest(name = "{9}")
      @MethodSource("scenarios")
      void given_leaf_update_should_compute_expected_parent_scores(
          boolean isGroup,
          Double agentA1Pre,
          Double agentA2Pre,
          Double assetAPre,
          Double assetBPre,
          String target,
          Double updateScore,
          Double expectedAssetA,
          Double expectedAssetGroup,
          String scenario) {
        // Arrange
        Hierarchy h = createHierarchy(isGroup);
        preScore(h.agentA1, agentA1Pre);
        preScore(h.agentA2, agentA2Pre);
        preScore(h.assetA, assetAPre);
        preScore(h.assetB, assetBPre);
        entityManager.flush();
        entityManager.refresh(h.inject);

        ExpectationUpdateInput input =
            ExpectationFixture.getExpectationUpdateInput(SOURCE_ID, updateScore);

        // Act
        injectExpectationService.updateInjectExpectationUsingBehaviors(
            resolveTarget(h, target).getId(), input);
        entityManager.flush();
        entityManager.clear();

        // Assert
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(h.inject.getId());

        assertThat(findById(saved, h.assetA.getId()).getScore()).isEqualTo(expectedAssetA);
        assertThat(findById(saved, h.assetGroup.getId()).getScore()).isEqualTo(expectedAssetGroup);
      }
    }

    // -- Asset-level updates (propagates down to agents) --

    @Nested
    @DisplayName("Asset-level update")
    class AssetLevelUpdate {

      static Stream<Arguments> scenarios() {
        return Stream.of(
            Arguments.of(
                false,
                null,
                100.0,
                100.0,
                null,
                "allMustValidate: update assetA, agents scored, assetA success, group pending"),
            Arguments.of(
                false,
                100.0,
                100.0,
                100.0,
                100.0,
                "allMustValidate: assetB success, update assetA, all success"),
            Arguments.of(
                true,
                null,
                100.0,
                100.0,
                100.0,
                "atLeastOne: update assetA, agents scored, assetA and group success"),
            Arguments.of(
                true,
                80.0,
                100.0,
                100.0,
                100.0,
                "atLeastOne: assetB failed, update assetA, assetA and group success"));
      }

      @ParameterizedTest(name = "{5}")
      @MethodSource("scenarios")
      void given_asset_update_should_propagate_to_agents_and_recompute_parents(
          boolean isGroup,
          Double assetBPre,
          Double updateScore,
          Double expectedAssetA,
          Double expectedAssetGroup,
          String scenario) {
        // Arrange
        Hierarchy h = createHierarchy(isGroup);
        preScore(h.assetB, assetBPre);
        entityManager.flush();
        entityManager.refresh(h.inject);

        ExpectationUpdateInput input =
            ExpectationFixture.getExpectationUpdateInput(SOURCE_ID, updateScore);

        // Act
        injectExpectationService.updateInjectExpectationUsingBehaviors(h.assetA.getId(), input);
        entityManager.flush();
        entityManager.clear();

        // Assert
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(h.inject.getId());

        // Agents should have received the result
        assertThat(findById(saved, h.agentA1.getId()).getScore()).isEqualTo(updateScore);
        assertThat(findById(saved, h.agentA2.getId()).getScore()).isEqualTo(updateScore);

        // Asset and group scores should be recomputed
        assertThat(findById(saved, h.assetA.getId()).getScore()).isEqualTo(expectedAssetA);
        assertThat(findById(saved, h.assetGroup.getId()).getScore()).isEqualTo(expectedAssetGroup);
      }
    }

    // -- Asset-group update should throw --

    @Test
    @DisplayName("given asset-group update should throw IllegalArgumentException")
    void given_asset_group_update_should_throw() {
      // Arrange
      Hierarchy h = createHierarchy(false);
      ExpectationUpdateInput input = ExpectationFixture.getExpectationUpdateInput(SOURCE_ID, 100.0);

      // Act & Assert
      assertThatThrownBy(
              () ->
                  injectExpectationService.updateInjectExpectationUsingBehaviors(
                      h.assetGroup.getId(), input))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
