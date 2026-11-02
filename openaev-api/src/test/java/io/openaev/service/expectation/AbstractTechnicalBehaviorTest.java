package io.openaev.service.expectation;

import static io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig.COLLECTOR_ID;
import static io.openaev.service.InjectExpectationUtils.FAILED_SCORE_VALUE;
import static io.openaev.utils.ExpectationSignatureUtils.EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME;
import static io.openaev.utils.ExpectationSignatureUtils.EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.openaev.IntegrationTest;
import io.openaev.collectors.expectations_expiration_manager.ExpectationsExpirationManagerJob;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.inject.form.InjectExpectationUpdateFromCollectorInput;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
class AbstractTechnicalBehaviorTest extends IntegrationTest {

  @Autowired private DetectionBehavior detectionBehavior;
  @Autowired private VulnerabilityBehavior vulnerabilityBehavior;
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
  @Autowired private CollectorService collectorService;
  @Autowired private ExpectationsExpirationManagerJob expectationsExpirationManagerJob;

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
    persistCollector("collector-siem", "EDR");
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

  private void persistCollector(String name, String type) {
    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(name))
        .withSecurityPlatform(
            securityPlatformComposer.forSecurityPlatform(
                SecurityPlatformFixture.createDefault(name, type)))
        .persist();
  }

  private Endpoint persistEndpointWithOneAgent() {
    return endpointComposer
        .forEndpoint(EndpointFixture.createEndpoint())
        .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
        .persist()
        .get();
  }

  private List<BaseInjectExpectation> actAndGetSavedExpectations(
      ExecutableInject executableInject, DetectionInjectExpectation template, String source) {
    detectionBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
        executableInject, template, source);
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
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

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
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

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
        "given no security platform collector can answer, should create leaves resolved as failed")
    void given_no_matching_collector_should_create_failed_leaves() {
      // Arrange
      Endpoint endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
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

      DetectionInjectExpectation template = createTemplate(inject);
      // Only an EDR collector is persisted (see setUp), so an XDR-restricted detection expectation
      // can never be answered by any collector on this tenant.
      template.setExpectedSecurityPlatforms(List.of(SecurityPlatform.SECURITY_PLATFORM_TYPE.XDR));

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

      // Act
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, template, "oaev");

      // Assert — expectations are still created (1 agent + 1 asset) and resolved as failure
      assertThat(saved).hasSize(2);
      assertThat(saved).allSatisfy(e -> assertThat(e.getScore()).isEqualTo(FAILED_SCORE_VALUE));
      // Every row is stamped with the frozen no-collector marker so the verdict stays explainable.
      assertThat(saved)
          .allSatisfy(
              e ->
                  assertThat(((TechnicalInjectExpectation) e).isCollectorMissingAtInit()).isTrue());

      // Leaves keep empty results so they stay manually fillable: the failure is carried by the
      // score alone, not by a synthetic result row.
      TechnicalInjectExpectation agentExpectation =
          (TechnicalInjectExpectation)
              saved.stream()
                  .filter(e -> ((TechnicalInjectExpectation) e).getAgent() != null)
                  .findFirst()
                  .orElseThrow();
      assertThat(agentExpectation.getResults()).isEmpty();
    }

    @Test
    @DisplayName(
        "given a matching collector is connected, should not flag the expectation as"
            + " collector-missing-at-init")
    void given_matching_collector_should_not_flag_collector_missing_at_init() {
      // Arrange
      Endpoint endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
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

      DetectionInjectExpectation template = createTemplate(inject);
      // An EDR collector is persisted in setUp, so an EDR-restricted detection expectation can be
      // answered: the gate must not trip.
      template.setExpectedSecurityPlatforms(List.of(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR));

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

      // Act
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, template, "oaev");

      // Assert — the frozen marker stays false, so a later collector removal cannot fabricate the
      // "no automated security platform" state after the fact.
      assertThat(saved)
          .allSatisfy(
              e ->
                  assertThat(((TechnicalInjectExpectation) e).isCollectorMissingAtInit())
                      .isFalse());
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
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

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
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

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
              List.of(),
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
              false,
              false,
              inject,
              List.of(),
              List.of(endpoint),
              List.of(assetGroup),
              List.of(),
              List.of());

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

      persistCollector("collector-edr", "EDR");
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
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

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
        "given one asset agentless, should set signatures on asset level but seed no placeholder result")
    void
        given_one_asset_agentless_should_set_signatures_but_no_placeholder_result_on_asset_level() {
      // Arrange
      Endpoint endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();

      persistCollector("collector-edr", "EDR");

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
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

      // Act
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "nmap");

      // Assert — 1 asset-level expectation (agentless, no agent expectation)
      assertThat(saved).hasSize(1);

      TechnicalInjectExpectation assetExpectation = (TechnicalInjectExpectation) saved.getFirst();
      assertThat(assetExpectation.getAsset()).isNotNull();
      assertThat(assetExpectation.getAgent()).isNull();

      // Results — an agentless leaf is answered by a single direct verdict written on the row, so
      // no per-collector placeholder is seeded: a placeholder left unanswered next to the real
      // verdict would keep the row pending forever (computeScore waits for every seeded source
      // and the expiration manager skips agentless rows that already carry a result).
      assertThat(assetExpectation.getResults()).isEmpty();

      // Signatures — set on asset level
      assertThat(assetExpectation.getSignatures()).isNotEmpty();
    }

    @Test
    @DisplayName(
        "given one asset agentless, should keep the expiration ordering guarantee of the expected collectors")
    void given_one_asset_agentless_should_keep_expiration_floor_of_expected_collectors() {
      // Arrange — an EDR collector polling every 300s, an agentless endpoint reached by a non-agent
      // injector and a detection expectation expiring long before two of its poll cycles
      Endpoint endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();

      Collector slowCollector = CollectorFixture.createDefaultCollector("collector-slow-edr");
      slowCollector.setPeriod(300);
      collectorComposer
          .forCollector(slowCollector)
          .withSecurityPlatform(
              securityPlatformComposer.forSecurityPlatform(
                  SecurityPlatformFixture.createDefault("collector-slow-edr", "EDR")))
          .persist();

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

      DetectionInjectExpectation template = createTemplate(inject);
      template.setExpirationTime(30L);
      template.setExpectedSecurityPlatforms(List.of(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR));

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

      // Act
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, template, "nmap");

      // Assert — still no placeholder on the agentless leaf, but the expiration manager may only
      // conclude it after two poll cycles of the slowest expected collector (2 x 300s)
      assertThat(saved).hasSize(1);
      TechnicalInjectExpectation assetExpectation = (TechnicalInjectExpectation) saved.getFirst();
      assertThat(assetExpectation.getAgent()).isNull();
      assertThat(assetExpectation.getResults()).isEmpty();
      assertThat(assetExpectation.getExpirationTime()).isEqualTo(600L);
    }

    @Test
    @DisplayName(
        "given one asset group with three assets should create a single asset-group expectation")
    void given_one_asset_group_with_three_assets_should_create_a_single_asset_group_expectation() {
      // Arrange
      Endpoint endpoint1 = persistEndpointWithOneAgent();
      Endpoint endpoint2 = persistEndpointWithOneAgent();
      Endpoint endpoint3 = persistEndpointWithOneAgent();

      AssetGroup assetGroup =
          assetGroupComposer
              .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("Group"))
              .withAsset(endpointComposer.forEndpoint(endpoint1))
              .withAsset(endpointComposer.forEndpoint(endpoint2))
              .withAsset(endpointComposer.forEndpoint(endpoint3))
              .persist()
              .get();

      Exercise exercise = persistDefaultExercise();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withAssetGroup(assetGroupComposer.forAssetGroup(assetGroup))
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
              List.of(assetGroup),
              List.of(),
              List.of());

      // Act
      List<BaseInjectExpectation> saved =
          actAndGetSavedExpectations(executableInject, createTemplate(inject), "oaev");

      // Assert — 3 agents + 3 assets + ONE asset group = 7 (never one group row per asset)
      assertThat(saved).hasSize(7);
      assertThat(countAgentExpectations(saved)).isEqualTo(3);
      assertThat(countAssetOnlyExpectations(saved)).isEqualTo(3);
      assertThat(countAssetGroupOnlyExpectations(saved)).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "given an agentless vulnerability expectation, the assessment injector verdict should conclude the row")
    void given_agentless_vulnerability_expectation_injector_verdict_should_conclude_the_row() {
      // Arrange — an agentless endpoint scanned by an assessment injector (e.g. Nuclei) that
      // declares its own security platform entry.
      Endpoint endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();

      Exercise exercise = persistDefaultExercise();

      Injector scannerInjector = InjectorFixture.createDefaultInjector("nuclei");
      scannerInjector.setPayloads(false);
      Inject defaultInject = InjectFixture.getDefaultInject();
      defaultInject.setInjector(scannerInjector);

      Inject inject =
          injectComposer
              .forInject(defaultInject)
              .withEndpoint(endpointComposer.forEndpoint(endpoint))
              .withExercise(exerciseComposer.forExercise(exercise))
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(scannerInjector))
              .persist()
              .get();

      VulnerabilityInjectExpectation template = new VulnerabilityInjectExpectation();
      template.setExpectedScore(100.0);
      template.setExpirationTime(21600L);
      template.setInject(inject);

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

      vulnerabilityBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
          executableInject, template, "nuclei");
      entityManager.flush();

      List<BaseInjectExpectation> saved =
          injectExpectationRepository.findAllByInjectId(inject.getId());
      assertThat(saved).hasSize(1);
      TechnicalInjectExpectation leaf = (TechnicalInjectExpectation) saved.getFirst();
      assertThat(leaf.getAgent()).isNull();
      assertThat(leaf.getResults()).isEmpty();
      assertThat(leaf.getScore()).isNull();

      // An assessment injector declares itself as a VULNERABILITY_SCANNER security platform and
      // is the verdict source of the rows it concludes (see SECURITY_PLATFORM_TYPE).
      SecurityPlatform scanner =
          securityPlatformComposer
              .forSecurityPlatform(
                  SecurityPlatformFixture.createDefault(
                      "Nuclei",
                      SecurityPlatform.SECURITY_PLATFORM_TYPE.VULNERABILITY_SCANNER.name()))
              .persist()
              .get();

      // Act — the injector writes its "Not vulnerable" verdict on the agentless row
      injectExpectationService.updateInjectExpectationFromSecurityPlatform(
          leaf.getId(),
          InjectExpectationUpdateFromCollectorInput.builder()
              .result("Not vulnerable")
              .isSuccess(true)
              .build(),
          scanner);
      entityManager.flush();
      entityManager.clear();

      // Assert — the single direct verdict concludes the row instead of leaving it pending next
      // to an unanswered placeholder
      TechnicalInjectExpectation concluded =
          (TechnicalInjectExpectation)
              injectExpectationRepository.findById(leaf.getId()).orElseThrow();
      assertThat(concluded.getScore()).isEqualTo(100.0);
      assertThat(concluded.getResults())
          .extracting(InjectExpectationResult::getSourceName, InjectExpectationResult::getResult)
          .containsExactly(tuple("Nuclei", "Not vulnerable"));
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

      persistCollector("collector-edr", "EDR");
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
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

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

      persistCollector("collector-edr", "EDR");
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
              false, false, inject, List.of(), List.of(endpoint), List.of(), List.of(), List.of());

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
                        .sourceType("unit-test")
                        .sourcePlatform("test-harness")
                        .sourceName("unit tests")
                        .date(Instant.now())
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

        ExpectationVerdict input = ExpectationFixture.getExpectationVerdict(SOURCE_ID, updateScore);

        // Act
        injectExpectationService.updateInjectExpectation(resolveTarget(h, target).getId(), input);
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

        ExpectationVerdict input = ExpectationFixture.getExpectationVerdict(SOURCE_ID, updateScore);

        // Act
        injectExpectationService.updateInjectExpectation(h.assetA.getId(), input);
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

    // -- Collector verdict (success flag scored on the answered expectation, metadata) --

    @Nested
    @DisplayName("Collector verdict")
    class CollectorVerdict {

      private static final String COLLECTOR_RESULT = "Alert raised";

      private Collector persistEdrCollector() {
        return collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector("collector-edr"))
            .persist()
            .get();
      }

      private InjectExpectationResult findResult(
          List<BaseInjectExpectation> saved, String expectationId, String sourceId) {
        return findById(saved, expectationId).getResults().stream()
            .filter(result -> sourceId.equals(result.getSourceId()))
            .findFirst()
            .orElseThrow();
      }

      @Test
      @DisplayName("given collector success on agent should score leaf and keep metadata")
      void given_collector_success_on_agent_should_score_leaf_and_keep_metadata() {
        // Arrange
        Hierarchy h = createHierarchy(false);
        Collector collector = persistEdrCollector();
        InjectExpectationUpdateFromCollectorInput input =
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                collector.getId(), COLLECTOR_RESULT, true);

        // Act
        injectExpectationService.updateInjectExpectation(h.agentA1.getId(), input);
        entityManager.flush();
        entityManager.clear();

        // Assert
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(h.inject.getId());
        InjectExpectationResult result = findResult(saved, h.agentA1.getId(), collector.getId());
        assertThat(result.getScore()).isEqualTo(h.agentA1.getExpectedScore());
        assertThat(result.getResult()).isEqualTo(h.agentA1.getSuccessLabel());
        assertThat(result.getMetadata()).isEqualTo(input.getMetadata());
        assertThat(findById(saved, h.agentA1.getId()).getScore())
            .isEqualTo(h.agentA1.getExpectedScore());
      }

      @Test
      @DisplayName("given collector failure on asset should score each agent with failed score")
      void given_collector_failure_on_asset_should_score_each_agent_with_failed_score() {
        // Arrange
        Hierarchy h = createHierarchy(false);
        Collector collector = persistEdrCollector();
        InjectExpectationUpdateFromCollectorInput input =
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                collector.getId(), COLLECTOR_RESULT, false);

        // Act
        injectExpectationService.updateInjectExpectation(h.assetA.getId(), input);
        entityManager.flush();
        entityManager.clear();

        // Assert
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(h.inject.getId());
        assertThat(findResult(saved, h.agentA1.getId(), collector.getId()).getScore())
            .isEqualTo(FAILED_SCORE_VALUE);
        assertThat(findResult(saved, h.agentA2.getId(), collector.getId()).getScore())
            .isEqualTo(FAILED_SCORE_VALUE);
      }

      @Test
      @DisplayName(
          "given collector answering again without metadata should keep its previous metadata")
      void given_collector_answering_again_without_metadata_should_keep_its_previous_metadata() {
        // Arrange
        Hierarchy h = createHierarchy(false);
        Collector collector = persistEdrCollector();
        InjectExpectationUpdateFromCollectorInput firstInput =
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                collector.getId(), COLLECTOR_RESULT, true);
        injectExpectationService.updateInjectExpectation(h.agentA1.getId(), firstInput);
        entityManager.flush();
        InjectExpectationUpdateFromCollectorInput secondInput =
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                collector.getId(), COLLECTOR_RESULT, false);
        secondInput.setMetadata(null);

        // Act
        injectExpectationService.updateInjectExpectation(h.agentA1.getId(), secondInput);
        entityManager.flush();
        entityManager.clear();

        // Assert
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(h.inject.getId());
        assertThat(
                findById(saved, h.agentA1.getId()).getResults().stream()
                    .filter(result -> collector.getId().equals(result.getSourceId())))
            .hasSize(1);
        InjectExpectationResult result = findResult(saved, h.agentA1.getId(), collector.getId());
        assertThat(result.getScore()).isEqualTo(FAILED_SCORE_VALUE);
        assertThat(result.getMetadata()).isEqualTo(firstInput.getMetadata());
      }

      @Test
      @DisplayName("given collector success on asset group should score every leaf of the group")
      void given_collector_success_on_asset_group_should_score_every_leaf_of_the_group() {
        // Arrange
        Hierarchy h = createHierarchy(false);
        Collector collector = persistEdrCollector();
        InjectExpectationUpdateFromCollectorInput input =
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                collector.getId(), COLLECTOR_RESULT, true);

        // Act
        injectExpectationService.updateInjectExpectation(h.assetGroup.getId(), input);
        entityManager.flush();
        entityManager.clear();

        // Assert
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(h.inject.getId());
        for (DetectionInjectExpectation leaf : List.of(h.agentA1, h.agentA2, h.assetB)) {
          assertThat(findResult(saved, leaf.getId(), collector.getId()).getScore())
              .isEqualTo(leaf.getExpectedScore());
        }
        TechnicalInjectExpectation savedGroup = findById(saved, h.assetGroup.getId());
        assertThat(savedGroup.getResults())
            .noneMatch(result -> collector.getId().equals(result.getSourceId()));
        assertThat(savedGroup.getScore()).isEqualTo(savedGroup.getExpectedScore());
      }

      @Test
      @DisplayName(
          "given bulk collector verdicts should score leaves and skip unknown expectations")
      void given_bulk_collector_verdicts_should_score_leaves_and_skip_unknown_expectations() {
        // Arrange
        Hierarchy h = createHierarchy(false);
        Collector collector = persistEdrCollector();
        Map<String, InjectExpectationUpdateFromCollectorInput> inputs =
            Map.of(
                h.assetA.getId(),
                InjectExpectationFixture.getInjectExpectationUpdateInput(
                    collector.getId(), COLLECTOR_RESULT, true),
                h.assetB.getId(),
                InjectExpectationFixture.getInjectExpectationUpdateInput(
                    collector.getId(), COLLECTOR_RESULT, false),
                "unknown-expectation-id",
                InjectExpectationFixture.getInjectExpectationUpdateInput(
                    collector.getId(), COLLECTOR_RESULT, true));

        // Act
        injectExpectationService.bulkUpdateInjectExpectation(inputs);
        entityManager.flush();
        entityManager.clear();

        // Assert
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(h.inject.getId());
        assertThat(findById(saved, h.agentA1.getId()).getScore())
            .isEqualTo(h.agentA1.getExpectedScore());
        assertThat(findById(saved, h.agentA2.getId()).getScore())
            .isEqualTo(h.agentA2.getExpectedScore());
        assertThat(findById(saved, h.assetA.getId()).getScore())
            .isEqualTo(h.assetA.getExpectedScore());
        assertThat(findById(saved, h.assetB.getId()).getScore()).isEqualTo(FAILED_SCORE_VALUE);
        // All assets must succeed: the failed agentless asset makes the group fail
        assertThat(findById(saved, h.assetGroup.getId()).getScore()).isEqualTo(FAILED_SCORE_VALUE);
      }
    }

    // -- Vulnerability verdicts of agentless assets, rolled up to their asset group --

    @Nested
    @DisplayName("Vulnerability verdict of an asset group")
    class VulnerabilityAssetGroupVerdict {

      private static final String SCAN_RESULT = "Scan finished";

      private VulnerabilityInjectExpectation buildVulnerabilityExpectation(
          Inject inject, Endpoint asset, AssetGroup group, boolean isGroup) {
        VulnerabilityInjectExpectation expectation =
            InjectExpectationFixture.createVulnerabilityInjectExpectation(inject, null);
        expectation.setAsset(asset);
        expectation.setAssetGroup(group);
        expectation.setExpectationGroup(isGroup);
        return expectation;
      }

      static Stream<Arguments> scenarios() {
        return Stream.of(
            Arguments.of(
                false,
                true,
                false,
                false,
                "allMustSucceed: one vulnerable asset makes the group vulnerable"),
            Arguments.of(
                true,
                true,
                false,
                true,
                "atLeastOne: the group stays clean while one asset is clean"),
            Arguments.of(
                true,
                true,
                true,
                false,
                "atLeastOne: the group is vulnerable when every asset is vulnerable"));
      }

      @ParameterizedTest(name = "{4}")
      @MethodSource("scenarios")
      void given_collector_verdicts_on_assets_should_derive_group_verdict(
          boolean isGroup,
          boolean isAssetOneVulnerable,
          boolean isAssetTwoVulnerable,
          boolean isGroupClean,
          String scenario) {
        // Arrange
        Endpoint endpointOne =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();
        Endpoint endpointTwo =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();
        AssetGroup group =
            assetGroupComposer
                .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("Scanned group"))
                .withAsset(endpointComposer.forEndpoint(endpointOne))
                .withAsset(endpointComposer.forEndpoint(endpointTwo))
                .persist()
                .get();
        Inject inject =
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withExercise(exerciseComposer.forExercise(persistDefaultExercise()))
                .persist()
                .get();
        VulnerabilityInjectExpectation assetOne =
            buildVulnerabilityExpectation(inject, endpointOne, group, isGroup);
        VulnerabilityInjectExpectation assetTwo =
            buildVulnerabilityExpectation(inject, endpointTwo, group, isGroup);
        VulnerabilityInjectExpectation assetGroup =
            buildVulnerabilityExpectation(inject, null, group, isGroup);
        injectExpectationRepository.saveAll(List.of(assetOne, assetTwo, assetGroup));
        entityManager.flush();
        entityManager.refresh(inject);
        Collector collector =
            collectorComposer
                .forCollector(CollectorFixture.createDefaultCollector("collector-vulnerability"))
                .persist()
                .get();

        // Act
        injectExpectationService.updateInjectExpectation(
            assetOne.getId(),
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                collector.getId(), SCAN_RESULT, !isAssetOneVulnerable));
        injectExpectationService.updateInjectExpectation(
            assetTwo.getId(),
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                collector.getId(), SCAN_RESULT, !isAssetTwoVulnerable));
        entityManager.flush();
        entityManager.clear();

        // Assert
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(inject.getId());
        TechnicalInjectExpectation savedGroup = findById(saved, assetGroup.getId());
        assertThat(savedGroup.getScore())
            .isEqualTo(isGroupClean ? savedGroup.getExpectedScore() : FAILED_SCORE_VALUE);
        // A group is derived from its assets, never answered directly
        assertThat(savedGroup.getResults())
            .noneMatch(result -> collector.getId().equals(result.getSourceId()));
      }
    }

    // -- Vulnerability verdict of an asset with agents (injector scan without security platform) --

    @Nested
    @DisplayName("Vulnerability verdict of an asset with agents")
    class VulnerabilityAssetWithAgentsVerdict {

      private static final String SCAN_RESULT = "Scan finished";

      private record AssetWithAgents(
          Inject inject,
          VulnerabilityInjectExpectation agentOne,
          VulnerabilityInjectExpectation agentTwo,
          VulnerabilityInjectExpectation asset) {}

      private VulnerabilityInjectExpectation buildVulnerabilityExpectation(
          Inject inject, Agent agent, Endpoint asset, Collector... sources) {
        VulnerabilityInjectExpectation expectation =
            InjectExpectationFixture.createVulnerabilityInjectExpectation(inject, agent);
        expectation.setAsset(asset);
        if (agent != null) {
          // Pending row of each answering source, as seeded on the agent leaves at initialization
          expectation.setResults(
              new java.util.ArrayList<>(
                  Stream.of(sources)
                      .map(
                          source ->
                              InjectExpectationResult.builder()
                                  .sourceId(source.getId())
                                  .sourceName(source.getName())
                                  .sourcePlatform(
                                      Optional.ofNullable(source.getSecurityPlatform())
                                          .map(Asset::getType)
                                          .orElse(null))
                                  .date(Instant.now())
                                  .build())
                      .toList()));
        }
        return expectation;
      }

      private Collector persistVulnerabilityCollector(String name) {
        return collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector(name))
            .persist()
            .get();
      }

      private AssetWithAgents persistAssetWithAgents(Collector... sources) {
        Endpoint endpoint =
            endpointComposer
                .forEndpoint(EndpointFixture.createEndpoint())
                .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession()))
                .persist()
                .get();
        Inject inject =
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withExercise(exerciseComposer.forExercise(persistDefaultExercise()))
                .persist()
                .get();
        VulnerabilityInjectExpectation agentOne =
            buildVulnerabilityExpectation(inject, endpoint.getAgents().get(0), endpoint, sources);
        VulnerabilityInjectExpectation agentTwo =
            buildVulnerabilityExpectation(inject, endpoint.getAgents().get(1), endpoint, sources);
        VulnerabilityInjectExpectation asset =
            buildVulnerabilityExpectation(inject, null, endpoint, sources);
        injectExpectationRepository.saveAll(List.of(agentOne, agentTwo, asset));
        entityManager.flush();
        entityManager.refresh(inject);
        return new AssetWithAgents(inject, agentOne, agentTwo, asset);
      }

      /** Expires the agents with the expiration collector: silence means "Not vulnerable" */
      private void expireAgents(AssetWithAgents target) throws Exception {
        String tenantId = target.inject().getTenant().getId();
        expectationsExpirationManagerJob.registerForTenant(tenantId);
        Collector expirationCollector = collectorService.collector(COLLECTOR_ID, tenantId);
        injectExpectationService.expireExpectations(
            List.of(target.agentOne(), target.agentTwo(), target.asset()), expirationCollector);
        entityManager.flush();
        entityManager.clear();
      }

      @ParameterizedTest(name = "vulnerable: {0}")
      @ValueSource(booleans = {true, false})
      void given_verdict_on_asset_with_agents_should_score_agents_and_derive_asset(
          boolean isVulnerable) {
        // Arrange
        Collector collector = persistVulnerabilityCollector("collector-vulnerability");
        AssetWithAgents target = persistAssetWithAgents(collector);
        Inject inject = target.inject();
        VulnerabilityInjectExpectation agentOne = target.agentOne();
        VulnerabilityInjectExpectation agentTwo = target.agentTwo();
        VulnerabilityInjectExpectation asset = target.asset();

        // Act
        injectExpectationService.updateInjectExpectation(
            asset.getId(),
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                collector.getId(), SCAN_RESULT, !isVulnerable));
        entityManager.flush();
        entityManager.clear();

        // Assert
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(inject.getId());
        Double expectedScore = isVulnerable ? FAILED_SCORE_VALUE : asset.getExpectedScore();
        for (VulnerabilityInjectExpectation agent : List.of(agentOne, agentTwo)) {
          TechnicalInjectExpectation savedAgent = findById(saved, agent.getId());
          assertThat(savedAgent.getResults())
              .singleElement()
              .satisfies(
                  result -> {
                    assertThat(result.getSourceId()).isEqualTo(collector.getId());
                    assertThat(result.getScore()).isEqualTo(expectedScore);
                    assertThat(result.getResult())
                        .isEqualTo(
                            isVulnerable
                                ? savedAgent.getFailureLabel()
                                : savedAgent.getSuccessLabel());
                  });
          assertThat(savedAgent.getScore()).isEqualTo(expectedScore);
        }
        // The verdict lands on the agents: the asset is derived from them, not answered directly
        TechnicalInjectExpectation savedAsset = findById(saved, asset.getId());
        assertThat(savedAsset.getResults())
            .noneMatch(result -> collector.getId().equals(result.getSourceId()));
        assertThat(savedAsset.getScore()).isEqualTo(expectedScore);
      }

      @ParameterizedTest(name = "vulnerable: {0}")
      @ValueSource(booleans = {true, false})
      void given_verdict_after_agents_expiration_should_score_agents_and_derive_asset(
          boolean isVulnerable) throws Exception {
        // Arrange
        Collector collector = persistVulnerabilityCollector("collector-vulnerability");
        AssetWithAgents target = persistAssetWithAgents(collector);
        expireAgents(target);

        // Act
        injectExpectationService.updateInjectExpectation(
            target.asset().getId(),
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                collector.getId(), SCAN_RESULT, !isVulnerable));
        entityManager.flush();
        entityManager.clear();

        // Assert — the expiration defaults to "Not vulnerable", a later finding still wins
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(target.inject().getId());
        Double expectedScore =
            isVulnerable ? FAILED_SCORE_VALUE : target.asset().getExpectedScore();
        for (VulnerabilityInjectExpectation agent : List.of(target.agentOne(), target.agentTwo())) {
          TechnicalInjectExpectation savedAgent = findById(saved, agent.getId());
          assertThat(savedAgent.getResults())
              .anyMatch(result -> COLLECTOR_ID.equals(result.getSourceId()));
          assertThat(savedAgent.getScore()).isEqualTo(expectedScore);
        }
        assertThat(findById(saved, target.asset().getId()).getScore()).isEqualTo(expectedScore);
      }

      @Test
      @DisplayName("given one source vulnerable and another pending should keep the agent pending")
      void given_one_source_vulnerable_and_another_pending_should_keep_the_agent_pending() {
        // Arrange
        Collector vulnerableSource = persistVulnerabilityCollector("collector-vulnerable");
        Collector pendingSource = persistVulnerabilityCollector("collector-pending");
        AssetWithAgents target = persistAssetWithAgents(vulnerableSource, pendingSource);

        // Act
        injectExpectationService.updateInjectExpectation(
            target.agentOne().getId(),
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                vulnerableSource.getId(), SCAN_RESULT, false));
        entityManager.flush();
        entityManager.clear();

        // Assert — every expected source answers before the leaf is settled
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(target.inject().getId());
        assertThat(findById(saved, target.agentOne().getId()).getScore()).isNull();
      }

      @Test
      @DisplayName("given one source vulnerable and another not vulnerable should be vulnerable")
      void given_one_source_vulnerable_and_another_not_vulnerable_should_be_vulnerable() {
        // Arrange
        Collector vulnerableSource = persistVulnerabilityCollector("collector-vulnerable");
        Collector cleanSource = persistVulnerabilityCollector("collector-clean");
        AssetWithAgents target = persistAssetWithAgents(vulnerableSource, cleanSource);
        injectExpectationService.updateInjectExpectation(
            target.agentOne().getId(),
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                vulnerableSource.getId(), SCAN_RESULT, false));

        // Act
        injectExpectationService.updateInjectExpectation(
            target.agentOne().getId(),
            InjectExpectationFixture.getInjectExpectationUpdateInput(
                cleanSource.getId(), SCAN_RESULT, true));
        entityManager.flush();
        entityManager.clear();

        // Assert — "Not vulnerable" is only the absence of a finding: the finding wins
        List<BaseInjectExpectation> saved =
            injectExpectationRepository.findAllByInjectId(target.inject().getId());
        assertThat(findById(saved, target.agentOne().getId()).getScore())
            .isEqualTo(FAILED_SCORE_VALUE);
      }
    }

    // -- Asset-group update should throw --

    @Test
    @DisplayName("given asset-group update should throw IllegalArgumentException")
    void given_asset_group_update_should_throw() {
      // Arrange
      Hierarchy h = createHierarchy(false);
      ExpectationVerdict input = ExpectationFixture.getExpectationVerdict(SOURCE_ID, 100.0);

      // Act & Assert
      assertThatThrownBy(
              () -> injectExpectationService.updateInjectExpectation(h.assetGroup.getId(), input))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
