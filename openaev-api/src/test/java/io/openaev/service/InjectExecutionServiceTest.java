package io.openaev.service;

import static io.openaev.utils.fixtures.InjectExpectationFixture.createVulnerabilityInjectExpectation;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.service.InjectExecutionService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.rest.inject.service.StructuredOutputUtils;
import io.openaev.utils.ExpectationUtils;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.OutputParserFixture;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InjectExecutionServiceTest extends IntegrationTest {

  @Spy @InjectMocks private InjectExecutionService testInjectExecutionService;

  private Inject inject;
  private InjectExpectation injectExpectation;
  private Agent agent;

  @Spy private ObjectMapper mapper = new ObjectMapper();
  @Mock private InjectExpectationService injectExpectationService;
  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private StructuredOutputUtils structuredOutputUtils;
  @Mock private InjectStatusService injectStatusService;
  @Mock private FindingService findingService;

  @BeforeEach
  void setUp() {
    agent = AgentFixture.createDefaultAgentService();
    inject = InjectFixture.getDefaultInject();
    injectExpectation = createVulnerabilityInjectExpectation(inject, agent);
    inject.setExpectations(List.of(injectExpectation));
  }

  @Test
  void checkCveExpectation_NoOutputParsers_ShouldSetNotVulnerable() {
    Set<OutputParser> outputParsers = Set.of();
    ObjectNode structuredOutput = null;
    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      testInjectExecutionService.checkCveExpectation(
          outputParsers, structuredOutput, inject, agent);
      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), times(1));
    }
  }

  @Test
  void trigger_checkCveExpectation_When_ExecutionTraceStatus_Is_Success() {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setMessage("message");
    input.setOutputStructured(null);
    input.setOutputRaw("outputRaw");
    input.setStatus(ExecutionTraceStatus.SUCCESS.toString());
    input.setDuration(10);
    input.setAction(InjectExecutionAction.command_execution);

    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setName(ExecutionStatus.SUCCESS);
    inject.setStatus(injectStatus);
    OutputParser outputParser =
        OutputParserFixture.getOutputParser(
            Set.of(OutputParserFixture.getDefaultContractOutputElement()));
    Set<OutputParser> outputParsers = Set.of(outputParser);
    doNothing().when(testInjectExecutionService).checkCveExpectation(any(), any(), any(), any());
    testInjectExecutionService.processInjectExecution(inject, agent, input, outputParsers);
    verify(testInjectExecutionService, times(1)).checkCveExpectation(any(), any(), any(), any());
  }

  @Test
  void dont_Trigger_checkCveExpectation_When_ExecutionTraceStatus_Is_Not_Success() {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setMessage("message");
    input.setOutputStructured(null);
    input.setOutputRaw("outputRaw");
    input.setStatus(ExecutionTraceStatus.ERROR.toString());
    input.setDuration(10);
    input.setAction(InjectExecutionAction.command_execution);

    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setName(ExecutionStatus.SUCCESS);
    inject.setStatus(injectStatus);
    OutputParser outputParser =
        OutputParserFixture.getOutputParser(
            Set.of(OutputParserFixture.getDefaultContractOutputElement()));
    Set<OutputParser> outputParsers = Set.of(outputParser);
    doNothing().when(testInjectExecutionService).checkCveExpectation(any(), any(), any(), any());
    testInjectExecutionService.processInjectExecution(inject, agent, input, outputParsers);
    verify(testInjectExecutionService, times(0)).checkCveExpectation(any(), any(), any(), any());
  }

  @Test
  void checkCveExpectation_NullStructuredOutput_ShouldSetNotVulnerable() {
    Set<OutputParser> outputParsers = Set.of(OutputParserFixture.getDefaultOutputParser());
    ObjectNode structuredOutput = null;
    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      testInjectExecutionService.checkCveExpectation(
          outputParsers, structuredOutput, inject, agent);
      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), times(1));
    }
  }

  @Test
  void checkCveExpectation_NoCveType_ShouldSetNotVulnerable() {
    Set<OutputParser> outputParsers = Set.of(OutputParserFixture.getDefaultOutputParser());
    ObjectNode structuredOutput = mapper.createObjectNode();
    structuredOutput
        .putArray("cve-key")
        .addObject()
        .put("id", "CVE-2025-0234")
        .put("host", "savacano28")
        .put("severity", "7.1");
    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      testInjectExecutionService.checkCveExpectation(
          outputParsers, structuredOutput, inject, agent);
      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), times(1));
    }
  }

  @Test
  void checkCveExpectation_HasCveTypeAndCveData_ShouldSetVulnerable() {
    ContractOutputElement CVEOutputElement = OutputParserFixture.getCVEOutputElement();
    Set<OutputParser> outputParsers =
        Set.of(OutputParserFixture.getOutputParser(Set.of(CVEOutputElement)));
    ObjectNode structuredOutput = mapper.createObjectNode();
    structuredOutput
        .putArray("cve-key")
        .addObject()
        .put("id", "CVE-2025-0234")
        .put("host", "savacano28")
        .put("severity", "7.1");

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      testInjectExecutionService.checkCveExpectation(
          outputParsers, structuredOutput, inject, agent);

      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), times(1));
    }
  }
}
