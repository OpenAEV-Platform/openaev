package io.openaev.service;

import static io.openaev.utils.fixtures.InjectExpectationFixture.createVulnerabilityInjectExpectation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.utils.ExpectationUtils;
import io.openaev.utils.fixtures.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InjectExpectationServiceTest {

  static final Long EXPIRATION_TIME_SIX_HOURS = 21600L;

  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Spy @InjectMocks private InjectExpectationService injectExpectationService;
  @Spy private ObjectMapper mapper = new ObjectMapper();

  private Inject inject;
  private Agent agent;

  @BeforeEach
  void setUp() {
    agent = AgentFixture.createDefaultAgentService();
    inject = InjectFixture.getDefaultInject();
    inject.setExpectations(List.of(createVulnerabilityInjectExpectation(inject, agent)));
  }

  private void mockExpectation(InjectExpectation expectation) {
    doReturn(expectation)
        .when(injectExpectationService)
        .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));
    when(injectExpectationRepository.saveAll(any())).thenReturn(List.of(expectation));
  }

  private ExecutionProcessingContext createContext(InjectExecutionInput input) {
    return new ExecutionProcessingContext(inject, agent, input, Map.of());
  }

  private InjectExecutionInput buildDefaultInput(ObjectNode structuredOutput) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setMessage("message");
    input.setOutputStructured(structuredOutput != null ? String.valueOf(structuredOutput) : null);
    input.setOutputRaw("outputRaw");
    input.setStatus(ExecutionTraceStatus.SUCCESS.toString());
    input.setDuration(10);
    input.setAction(InjectExecutionAction.command_execution);
    return input;
  }

  private void setupInjectWithOutputParser(OutputParser outputParser)
      throws JsonProcessingException {
    Injector injector = InjectorFixture.createDefaultInjector("InjectorName");
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setOutputParsers(outputParser != null ? Set.of(outputParser) : Set.of());
    InjectorContract contract =
        InjectorContractFixture.createPayloadInjectorContract(injector, payload);
    inject.setInjectorContract(contract);
  }

  private void setupVulnerabilityExpectation() {
    InjectExpectation expectation = createVulnerabilityInjectExpectation(inject, agent);
    inject.setExpectations(List.of(expectation));
    mockExpectation(expectation);
  }

  private void verifySetResultExpectationVulnerableCalledOnce(
      MockedStatic<ExpectationUtils> mocked) {
    mocked.verify(
        () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), times(1));
  }

  @Test
  @DisplayName("Should return all prevention expectations when none expired")
  void shouldReturnAllPreventionExpectationsWhenNoneExpired() {
    InjectExpectation expectation1 =
        InjectExpectationFixture.createPreventionInjectExpectation(inject, null);
    InjectExpectation expectation2 =
        InjectExpectationFixture.createPreventionInjectExpectation(inject, null);
    when(injectExpectationRepository.findAll(any()))
        .thenReturn(List.of(expectation1, expectation2));

    List<InjectExpectation> result =
        injectExpectationService.preventionExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(expectation1.getId(), result.getFirst().getId());
  }

  @Test
  @DisplayName("Should return all detection expectations when none expired")
  void shouldReturnAllDetectionExpectationsWhenNoneExpired() {
    InjectExpectation expectation1 =
        InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
    InjectExpectation expectation2 =
        InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
    when(injectExpectationRepository.findAll(any()))
        .thenReturn(List.of(expectation1, expectation2));

    List<InjectExpectation> result =
        injectExpectationService.detectionExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(expectation1.getId(), result.getFirst().getId());
  }

  @Test
  @DisplayName("Should return all manual expectations when none expired")
  void shouldReturnAllManualExpectationsWhenNoneExpired() {
    InjectExpectation expectation1 =
        InjectExpectationFixture.createManualInjectExpectation(null, inject);
    InjectExpectation expectation2 =
        InjectExpectationFixture.createManualInjectExpectation(null, inject);
    when(injectExpectationRepository.findAll(any()))
        .thenReturn(List.of(expectation1, expectation2));

    List<InjectExpectation> result =
        injectExpectationService.manualExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(expectation1.getId(), result.getFirst().getId());
  }

  @Test
  @DisplayName("Should set not vulnerable when no output parsers")
  void shouldSetNotVulnerableWhenNoOutputParsers() throws JsonProcessingException {
    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupInjectWithOutputParser(null);
      setupVulnerabilityExpectation();

      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(new InjectExecutionInput()), mapper.createObjectNode());

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    }
  }

  @Test
  @DisplayName("Should set not vulnerable when structured output is empty")
  void shouldSetNotVulnerableWhenEmptyStructuredOutput() {
    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupVulnerabilityExpectation();

      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    }
  }

  @Test
  @DisplayName("Should set not vulnerable when structured output has no CVE type")
  void shouldSetNotVulnerableWhenNoCveType() throws JsonProcessingException {
    ObjectNode structuredOutput = mapper.createObjectNode();
    structuredOutput
        .putArray("no-cve-key")
        .addObject()
        .put("id", "no-cve-id")
        .put("host", "savanna28")
        .put("severity", "7.1");

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupInjectWithOutputParser(
          OutputParserFixture.getOutputParser(
              Set.of(OutputParserFixture.getContractOutputElementTypeIPv6())));
      setupVulnerabilityExpectation();

      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(structuredOutput)), structuredOutput);

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    }
  }

  @Test
  @DisplayName("Should set vulnerable when structured output has CVE type and CVE data")
  void shouldSetVulnerableWhenHasCveTypeAndCveData() {
    ObjectNode structuredOutput = mapper.createObjectNode();
    structuredOutput
        .putArray("cve-key")
        .addObject()
        .put("id", "CVE-2025-0234")
        .put("host", "savacano28")
        .put("severity", "7.1");

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupInjectWithOutputParser(
          OutputParserFixture.getOutputParser(
              Set.of(OutputParserFixture.getContractOutputElementTypeIPv6())));
      setupVulnerabilityExpectation();

      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(structuredOutput)), structuredOutput);

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("Should set not vulnerable when structured output is an empty array")
  void shouldSetNotVulnerableWhenStructuredOutputIsEmptyArray() {
    // isArray()=true but size()=0 -> not vulnerable
    ArrayNode structuredOutput = mapper.createArrayNode();

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupVulnerabilityExpectation();

      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), structuredOutput);

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    }
  }

  @Test
  @DisplayName("Should set vulnerable when structured output is a non-empty array")
  void shouldSetVulnerableWhenStructuredOutputIsNonEmptyArray() {
    // isArray()=true and size()>0 -> vulnerable
    ArrayNode structuredOutput = mapper.createArrayNode();
    structuredOutput.addObject().put("id", "CVE-2025-9999");

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupVulnerabilityExpectation();

      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), structuredOutput);

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    }
  }

  @Test
  @DisplayName("Should do nothing when no vulnerability expectations match the agent")
  void shouldDoNothingWhenNoVulnerabilityExpectationsForAgent() {
    // Expectation belongs to a different agent -> filtered out -> early return
    Agent otherAgent = AgentFixture.createDefaultAgentService();
    InjectExpectation expectationForOtherAgent =
        createVulnerabilityInjectExpectation(inject, otherAgent);
    inject.setExpectations(List.of(expectationForOtherAgent));

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      // early return: nothing should be called
      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), never());
      verify(injectExpectationRepository, never()).saveAll(any());
    }
  }

  @Test
  @DisplayName("Should do nothing when expectations are not of vulnerability type")
  void shouldDoNothingWhenExpectationsAreNotVulnerabilityType() {
    // Only non-VULNERABILITY expectations -> filtered out -> early return
    InjectExpectation prevention =
        InjectExpectationFixture.createPreventionInjectExpectation(inject, null);
    InjectExpectation detection =
        InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
    inject.setExpectations(List.of(prevention, detection));

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), never());
      verify(injectExpectationRepository, never()).saveAll(any());
    }
  }

  @Test
  @DisplayName("Should do nothing when expectation has a null agent")
  void shouldDoNothingWhenExpectationHasNullAgent() {
    // exp.getAgent() == null -> filtered out -> early return
    InjectExpectation expectationWithNullAgent = createVulnerabilityInjectExpectation(inject, null);
    inject.setExpectations(List.of(expectationWithNullAgent));

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), never());
      verify(injectExpectationRepository, never()).saveAll(any());
    }
  }

  @Test
  @DisplayName("Should do nothing when inject has no expectations")
  void shouldDoNothingWhenInjectHasNoExpectations() {
    inject.setExpectations(List.of());

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), never());
      verify(injectExpectationRepository, never()).saveAll(any());
    }
  }

  @Test
  @DisplayName("Should save all expectations after processing")
  void shouldSaveAllExpectationsAfterProcessing() {
    setupVulnerabilityExpectation();

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      verify(injectExpectationRepository, times(1)).saveAll(any());
    }
  }

  @Test
  @DisplayName("Should call update for each vulnerability expectation")
  void shouldCallUpdateForEachVulnerabilityExpectation() {
    // Two vulnerability expectations for the same agent
    InjectExpectation exp1 = createVulnerabilityInjectExpectation(inject, agent);
    InjectExpectation exp2 = createVulnerabilityInjectExpectation(inject, agent);
    inject.setExpectations(List.of(exp1, exp2));
    doReturn(exp1)
        .when(injectExpectationService)
        .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));
    when(injectExpectationRepository.saveAll(any())).thenReturn(List.of(exp1, exp2));

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      injectExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      // updateInjectExpectation called once per expectation
      verify(injectExpectationService, times(2))
          .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));
      verify(injectExpectationRepository, times(1)).saveAll(any());
    }
  }
}
