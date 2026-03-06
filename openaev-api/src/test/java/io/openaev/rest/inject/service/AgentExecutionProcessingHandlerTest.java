package io.openaev.rest.inject.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.output_processor.OutputProcessor;
import io.openaev.output_processor.OutputProcessorFactory;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.OutputParserFixture;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentExecutionProcessingHandlerTest {

  @Mock private StructuredOutputUtils structuredOutputUtils;
  @Mock private OutputProcessorFactory outputProcessorFactory;
  @Mock private OutputProcessor mockProcessor;

  @InjectMocks private AgentExecutionProcessingHandler handler;

  private final ObjectMapper mapper = new ObjectMapper();
  private Inject inject;
  private Agent agent;

  @BeforeEach
  void setUp() {
    this.inject = InjectFixture.getDefaultInject();
    this.agent = AgentFixture.createDefaultAgentService();
  }

  @Test
  @DisplayName("Should return empty when status is not SUCCESS or action is not command execution")
  void shouldReturnEmptyWhenStatusNotSuccessOrActionNotExecution() throws Exception {
    InjectExecutionInput inputError =
        buildInput(ExecutionTraceStatus.ERROR, InjectExecutionAction.command_execution);
    assertTrue(
        handler
            .processContext(new ExecutionProcessingContext(inject, agent, inputError, Map.of()))
            .isEmpty());

    InjectExecutionInput inputComplete =
        buildInput(ExecutionTraceStatus.SUCCESS, InjectExecutionAction.complete);
    assertTrue(
        handler
            .processContext(new ExecutionProcessingContext(inject, agent, inputComplete, Map.of()))
            .isEmpty());

    verifyNoInteractions(structuredOutputUtils);
  }

  @Test
  @DisplayName("Should return empty when computeStructuredOutput returns no result")
  void shouldReturnEmptyWhenComputeStructuredOutputReturnsEmpty() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx();
    when(structuredOutputUtils.extractOutputParsers(any()))
        .thenReturn(Set.of(mock(OutputParser.class)));
    when(structuredOutputUtils.computeStructuredOutputFromOutputParsers(any(), anyString()))
        .thenReturn(Optional.empty());

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isEmpty());
    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should return empty and skip dispatch when output parsers are empty")
  void shouldReturnEmptyWhenOutputParsersAreEmpty() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx();
    when(structuredOutputUtils.extractOutputParsers(any())).thenReturn(Set.of());
    when(structuredOutputUtils.computeStructuredOutputFromOutputParsers(any(), anyString()))
        .thenReturn(Optional.empty());

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isEmpty());
    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should skip processor when contract element key is missing in produced JSON")
  void shouldSkipProcessorWhenKeyInContractIsMissingInProducedJson() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx();

    String uniqueMissingKey = "totally_absent_key_" + System.currentTimeMillis();
    ContractOutputElement element =
        OutputParserFixture.getContractOutputElement(
            ContractOutputType.Text, uniqueMissingKey, Set.of(), true);

    OutputParser parser = OutputParserFixture.getOutputParser(Set.of(element));
    when(structuredOutputUtils.extractOutputParsers(any())).thenReturn(Set.of(parser));

    ObjectNode json = mapper.createObjectNode();
    json.put("other_key", "val");
    ObjectNode spyJson = spy(json);
    doReturn(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.missingNode())
        .when(spyJson)
        .path(anyString());
    when(structuredOutputUtils.computeStructuredOutputFromOutputParsers(any(), anyString()))
        .thenReturn(Optional.of(spyJson));
    when(outputProcessorFactory.getProcessor(ContractOutputType.Text))
        .thenReturn(Optional.of(mockProcessor));

    handler.processContext(ctx);

    verify(mockProcessor, never()).process(any(), any(), any());
  }

  @Test
  @DisplayName("Should skip processor when no processor is registered for the output type")
  void shouldSkipWhenNoProcessorRegisteredForType() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx();

    ContractOutputElement element =
        OutputParserFixture.getContractOutputElement(ContractOutputType.CVE, "cve", Set.of(), true);
    OutputParser parser = OutputParserFixture.getOutputParser(Set.of(element));
    when(structuredOutputUtils.extractOutputParsers(any())).thenReturn(Set.of(parser));

    ObjectNode json = mapper.createObjectNode();
    json.put("cve-key", "some-value");
    when(structuredOutputUtils.computeStructuredOutputFromOutputParsers(any(), anyString()))
        .thenReturn(Optional.of(json));
    when(outputProcessorFactory.getProcessor(ContractOutputType.CVE)).thenReturn(Optional.empty());

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isPresent());
    verify(mockProcessor, never()).process(any(), any(), any());
  }

  @Test
  @DisplayName("Should process correctly when multiple parser elements match")
  void shouldProcessCorrectlyWhenMultipleParsersElementsMatch() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx();

    ContractOutputElement element =
        OutputParserFixture.getContractOutputElement(ContractOutputType.CVE, "cve", Set.of(), true);
    OutputParser parser = OutputParserFixture.getOutputParser(Set.of(element));
    when(structuredOutputUtils.extractOutputParsers(any())).thenReturn(Set.of(parser));

    ObjectNode json = mapper.createObjectNode();
    json.put("cve-key", "cve-data");

    when(structuredOutputUtils.computeStructuredOutputFromOutputParsers(any(), anyString()))
        .thenReturn(Optional.of(json));
    when(outputProcessorFactory.getProcessor(any())).thenReturn(Optional.of(mockProcessor));

    handler.processContext(ctx);

    verify(mockProcessor, times(1)).process(eq(ctx), any(), any(JsonNode.class));
  }

  @Test
  @DisplayName(
      "Should dispatch to processor with the correct node value from the structured output")
  void shouldDispatchToProcessorWithCorrectNodeValue() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx();

    ContractOutputElement element =
        OutputParserFixture.getContractOutputElement(ContractOutputType.CVE, "cve", Set.of(), true);
    OutputParser parser = OutputParserFixture.getOutputParser(Set.of(element));
    when(structuredOutputUtils.extractOutputParsers(any())).thenReturn(Set.of(parser));

    ObjectNode json = mapper.createObjectNode();
    json.put("cve-key", "expected-value");
    when(structuredOutputUtils.computeStructuredOutputFromOutputParsers(any(), anyString()))
        .thenReturn(Optional.of(json));
    when(outputProcessorFactory.getProcessor(ContractOutputType.CVE))
        .thenReturn(Optional.of(mockProcessor));

    handler.processContext(ctx);

    verify(mockProcessor, times(1))
        .process(
            eq(ctx),
            argThat(c -> "cve-key".equals(c.key()) && ContractOutputType.CVE.equals(c.type())),
            argThat(node -> "expected-value".equals(node.asText())));
  }

  private ExecutionProcessingContext createValidCtx() {
    return new ExecutionProcessingContext(
        inject,
        agent,
        buildInput(ExecutionTraceStatus.SUCCESS, InjectExecutionAction.command_execution),
        Map.of());
  }

  private InjectExecutionInput buildInput(
      ExecutionTraceStatus status, InjectExecutionAction action) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setStatus(status.toString());
    input.setAction(action);
    input.setMessage("raw-output");
    return input;
  }
}
