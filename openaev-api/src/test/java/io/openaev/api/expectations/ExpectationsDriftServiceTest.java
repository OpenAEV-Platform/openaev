package io.openaev.api.expectations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.expectations.dto.ExpectationsDriftOutput;
import io.openaev.api.expectations.dto.ExpectationsRealignOutput;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.utils.BulkDeleteChunkRunner;
import io.openaev.service.utils.BulkOperationMonitor;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Expectations drift detection and realignment")
class ExpectationsDriftServiceTest {

  private static final String SCENARIO_ID = "scenario-id";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private InjectRepository injectRepository;
  @Mock private ScenarioRepository scenarioRepository;
  @Mock private ExerciseRepository exerciseRepository;
  @Mock private BulkOperationMonitor bulkOperationMonitor;
  @Mock private BulkDeleteChunkRunner chunkRunner;

  private ExpectationsDriftService service;

  @BeforeEach
  void setUp() {
    service =
        new ExpectationsDriftService(
            injectRepository,
            scenarioRepository,
            exerciseRepository,
            new InjectorContractContentUtils(),
            bulkOperationMonitor,
            chunkRunner,
            MAPPER);
  }

  // -- FIXTURES --

  private static ObjectNode expectationNode(
      String type, boolean group, int score, boolean predefined, String... platforms) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("expectation_type", type);
    node.put("expectation_name", "Expect " + type);
    node.put("expectation_score", score);
    node.put("expectation_expectation_group", group);
    node.put("expectation_is_predefined", predefined);
    if (platforms.length > 0) {
      ArrayNode platformsNode = MAPPER.createArrayNode();
      for (String platform : platforms) {
        platformsNode.add(platform);
      }
      node.set("expectation_expected_security_platform_types", platformsNode);
    }
    return node;
  }

  /** Contract whose content exposes an expectations field with the given available expectations. */
  private static InjectorContract contractWithExpectations(JsonNode... availableExpectations) {
    ObjectNode field = MAPPER.createObjectNode();
    field.put("key", "expectations");
    field.put("type", "expectation");
    ArrayNode available = MAPPER.createArrayNode();
    for (JsonNode expectation : availableExpectations) {
      available.add(expectation);
    }
    field.set("availableExpectations", available);
    ObjectNode content = MAPPER.createObjectNode();
    content.set("fields", MAPPER.createArrayNode().add(field));
    return contractWithContent(content);
  }

  /** Contract without any expectations field in its content. */
  private static InjectorContract contractWithoutExpectations() {
    ObjectNode field = MAPPER.createObjectNode();
    field.put("key", "title");
    field.put("type", "text");
    ObjectNode content = MAPPER.createObjectNode();
    content.set("fields", MAPPER.createArrayNode().add(field));
    return contractWithContent(content);
  }

  private static InjectorContract contractWithContent(ObjectNode content) {
    InjectorContract contract = new InjectorContract();
    contract.setId(UUID.randomUUID().toString());
    contract.setContent(content.toString());
    contract.setConvertedContent(content);
    return contract;
  }

  private static Inject injectWith(InjectorContract contract, JsonNode... storedExpectations) {
    Inject inject = new Inject();
    inject.setId(UUID.randomUUID().toString());
    inject.setInjectorContract(contract);
    if (storedExpectations.length > 0) {
      ArrayNode expectations = MAPPER.createArrayNode();
      for (JsonNode expectation : storedExpectations) {
        expectations.add(expectation);
      }
      ObjectNode content = MAPPER.createObjectNode();
      content.set("expectations", expectations);
      inject.setContent(content);
    }
    return inject;
  }

  private void stubScenarioInjects(Inject... injects) {
    Set<Inject> set = new LinkedHashSet<>(List.of(injects));
    when(injectRepository.findByScenarioId(SCENARIO_ID)).thenReturn(set);
    Scenario scenario = new Scenario();
    scenario.setId(SCENARIO_ID);
    lenient().when(scenarioRepository.findById(SCENARIO_ID)).thenReturn(Optional.of(scenario));
    lenient().when(scenarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private void stubChunkRunnerPassthrough() {
    when(chunkRunner.call(any()))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
  }

  // -- DETECTION --

  @Test
  @DisplayName("No drift when stored expectations match the contract's predefined ones")
  void noDriftWhenAligned() {
    InjectorContract contract =
        contractWithExpectations(
            expectationNode("PREVENTION", false, 100, true),
            expectationNode("DETECTION", false, 100, true),
            // Non-predefined expectations are not part of the template.
            expectationNode("MANUAL", false, 100, false));
    Inject inject =
        injectWith(
            contract,
            expectationNode("PREVENTION", false, 100, true),
            expectationNode("DETECTION", false, 100, true));
    stubScenarioInjects(inject);

    ExpectationsDriftOutput output = service.scenarioDrift(SCENARIO_ID);

    assertThat(output.driftDetected()).isFalse();
    assertThat(output.driftedInjectCount()).isZero();
    assertThat(output.totalInjectCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("Drift detected when the contract gained a predefined expectation")
  void driftWhenContractGainedExpectation() {
    InjectorContract contract =
        contractWithExpectations(
            expectationNode("PREVENTION", false, 100, true),
            expectationNode("DETECTION", false, 100, true));
    Inject inject = injectWith(contract, expectationNode("PREVENTION", false, 100, true));
    stubScenarioInjects(inject);

    ExpectationsDriftOutput output = service.scenarioDrift(SCENARIO_ID);

    assertThat(output.driftDetected()).isTrue();
    assertThat(output.driftedInjectCount()).isEqualTo(1);
    assertThat(output.totalInjectCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("Ordering and tuning attributes (score, name) are not drift")
  void orderingAndTuningAttributesAreNotDrift() {
    InjectorContract contract =
        contractWithExpectations(
            expectationNode("PREVENTION", false, 100, true),
            expectationNode("DETECTION", true, 100, true));
    // Stored in reverse order, with adjusted scores and names.
    ObjectNode tunedDetection = expectationNode("DETECTION", true, 50, true);
    tunedDetection.put("expectation_name", "Custom detection wording");
    Inject inject =
        injectWith(contract, tunedDetection, expectationNode("PREVENTION", false, 30, true));
    stubScenarioInjects(inject);

    assertThat(service.scenarioDrift(SCENARIO_ID).driftDetected()).isFalse();
  }

  @Test
  @DisplayName("A change of expected security platform types is drift")
  void securityPlatformTypeChangeIsDrift() {
    InjectorContract contract =
        contractWithExpectations(expectationNode("DETECTION", false, 100, true, "EDR", "SIEM"));
    Inject inject = injectWith(contract, expectationNode("DETECTION", false, 100, true, "SIEM"));
    stubScenarioInjects(inject);

    assertThat(service.scenarioDrift(SCENARIO_ID).driftDetected()).isTrue();
  }

  @Test
  @DisplayName("Injects without stored expectations follow the contract dynamically - no drift")
  void injectWithoutStoredExpectationsNeverDrifts() {
    InjectorContract contract =
        contractWithExpectations(expectationNode("PREVENTION", false, 100, true));
    Inject inject = injectWith(contract);
    stubScenarioInjects(inject);

    ExpectationsDriftOutput output = service.scenarioDrift(SCENARIO_ID);

    assertThat(output.driftDetected()).isFalse();
    assertThat(output.totalInjectCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("An explicitly emptied expectations list is a customization - drift")
  void explicitlyEmptiedExpectationsListIsDrift() {
    // The user deliberately removed every expectation from the inject (the form persists an
    // explicit empty array). Execution respects that choice, so drift is how the divergence from
    // the contract template is surfaced - realignment being the opt-in way to restore it.
    InjectorContract contract =
        contractWithExpectations(expectationNode("PREVENTION", false, 100, true));
    Inject inject = injectWith(contract);
    ObjectNode content = MAPPER.createObjectNode();
    content.set("expectations", MAPPER.createArrayNode());
    inject.setContent(content);
    stubScenarioInjects(inject);

    ExpectationsDriftOutput output = service.scenarioDrift(SCENARIO_ID);

    assertThat(output.driftDetected()).isTrue();
    assertThat(output.driftedInjectCount()).isEqualTo(1);
    assertThat(output.totalInjectCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("Injects whose contract has no expectations field are excluded from the report")
  void injectWithoutExpectationsContractIsExcluded() {
    Inject inject =
        injectWith(contractWithoutExpectations(), expectationNode("PREVENTION", false, 100, true));
    stubScenarioInjects(inject);

    ExpectationsDriftOutput output = service.scenarioDrift(SCENARIO_ID);

    assertThat(output.driftDetected()).isFalse();
    assertThat(output.totalInjectCount()).isZero();
  }

  @Test
  @DisplayName("Drift report on a missing inject fails with element not found")
  void injectDriftThrowsWhenInjectMissing() {
    when(injectRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.injectDrift("missing"))
        .isInstanceOf(ElementNotFoundException.class);
  }

  // -- REALIGNMENT --

  @Test
  @DisplayName("Realign overwrites drifted injects with the contract template at full score")
  void realignOverwritesDriftedInjects() {
    InjectorContract contract =
        contractWithExpectations(
            expectationNode("PREVENTION", false, 100, true),
            expectationNode("DETECTION", false, 100, true));
    Inject drifted = injectWith(contract, expectationNode("PREVENTION", false, 30, true));
    Inject aligned =
        injectWith(
            contract,
            expectationNode("PREVENTION", false, 100, true),
            expectationNode("DETECTION", false, 100, true));
    ObjectNode alignedContentBefore = aligned.getContent().deepCopy();
    stubScenarioInjects(drifted, aligned);
    stubChunkRunnerPassthrough();
    when(bulkOperationMonitor.start(anyString(), anyString(), anyInt())).thenReturn("op-1");
    when(injectRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ExpectationsRealignOutput output = service.realignScenario(SCENARIO_ID);

    assertThat(output.realignedInjectCount()).isEqualTo(1);
    JsonNode expectations = drifted.getContent().get("expectations");
    assertThat(expectations).hasSize(2);
    expectations.forEach(
        expectation -> assertThat(expectation.get("expectation_score").asInt()).isEqualTo(100));
    // The already-aligned inject is untouched.
    assertThat(aligned.getContent()).isEqualTo(alignedContentBefore);
    // Realigned injects no longer drift.
    assertThat(service.hasDrift(drifted)).isFalse();
    verify(bulkOperationMonitor).start("realign", "inject expectations", 1);
    verify(bulkOperationMonitor).progress("op-1", 1);
    verify(bulkOperationMonitor).complete("op-1");
    verify(bulkOperationMonitor, never()).fail(anyString());
  }

  @Test
  @DisplayName("Realign is a no-op when nothing drifted")
  void realignNoOpWhenNothingDrifted() {
    InjectorContract contract =
        contractWithExpectations(expectationNode("PREVENTION", false, 100, true));
    Inject aligned = injectWith(contract, expectationNode("PREVENTION", false, 100, true));
    stubScenarioInjects(aligned);
    stubChunkRunnerPassthrough();

    ExpectationsRealignOutput output = service.realignScenario(SCENARIO_ID);

    assertThat(output.realignedInjectCount()).isZero();
    verifyNoInteractions(bulkOperationMonitor);
    verify(injectRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("Realign marks the massive operation as failed when a chunk fails")
  void realignFailsMonitorOnError() {
    InjectorContract contract =
        contractWithExpectations(
            expectationNode("PREVENTION", false, 100, true),
            expectationNode("DETECTION", false, 100, true));
    Inject drifted = injectWith(contract, expectationNode("PREVENTION", false, 100, true));
    stubScenarioInjects(drifted);
    stubChunkRunnerPassthrough();
    when(bulkOperationMonitor.start(anyString(), anyString(), anyInt())).thenReturn("op-1");
    when(injectRepository.saveAll(any())).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> service.realignScenario(SCENARIO_ID))
        .isInstanceOf(IllegalStateException.class);

    verify(bulkOperationMonitor).fail("op-1");
    verify(bulkOperationMonitor, never()).complete(anyString());
  }
}
