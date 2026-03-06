package io.openaev.rest.inject.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import io.openaev.output_processor.OutputProcessor;
import io.openaev.output_processor.OutputProcessorFactory;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import io.openaev.utils.fixtures.InjectFixture;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InjectorExecutionProcessingHandlerTest {

  @Mock private OutputProcessorFactory outputProcessorFactory;
  @Mock private InjectorContractContentUtils injectorContractContentUtils;
  @Mock private OutputProcessor mockProcessor;

  @InjectMocks private InjectorExecutionProcessingHandler handler;

  private final ObjectMapper mapper = new ObjectMapper();
  private Inject inject;
  private InjectorContract injectorContract;

  @BeforeEach
  void setUp() {
    this.inject = InjectFixture.getDefaultInject();
    this.injectorContract = mock(InjectorContract.class);
    inject.setInjectorContract(injectorContract);
    handler.mapper = mapper;
  }

  @Test
  @DisplayName("Should return empty if status is not SUCCESS or action is not COMPLETE")
  void shouldReturnEmptyWhenStatusNotSuccessOrActionNotComplete() throws Exception {
    // Case 1: Status is ERROR
    InjectExecutionInput inputError =
        buildInput(ExecutionTraceStatus.ERROR, InjectExecutionAction.complete, "{}");
    assertTrue(
        handler
            .processContext(new ExecutionProcessingContext(inject, null, inputError, Map.of()))
            .isEmpty());

    verifyNoInteractions(outputProcessorFactory);

    // Case 2: Action is NOT complete
    InjectExecutionInput inputWrongAction =
        buildInput(ExecutionTraceStatus.SUCCESS, InjectExecutionAction.command_execution, "{}");
    assertTrue(
        handler
            .processContext(
                new ExecutionProcessingContext(inject, null, inputWrongAction, Map.of()))
            .isEmpty());

    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should return empty when outputStructured is null")
  void shouldReturnEmptyWhenOutputStructuredIsNull() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx(null);

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isEmpty());
    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should return empty when outputStructured is blank")
  void shouldReturnEmptyWhenOutputStructuredIsBlank() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("   ");

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isEmpty());
    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should return empty and log warning when outputStructured is invalid JSON")
  void shouldReturnEmptyWhenOutputStructuredIsInvalidJson() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("not-valid-json{{{");

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isEmpty());
    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should return present result and skip processor when key is missing from JSON")
  void shouldSkipProcessorWhenKeyIsMissing() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("{\"unrelated_key\": \"value\"}");

    InjectorContractContentOutputElement element = new InjectorContractContentOutputElement();
    element.setField("missing_key");
    element.setFindingCompatible(true);

    when(injectorContract.getConvertedContent()).thenReturn(mapper.createObjectNode());
    when(injectorContractContentUtils.getContractOutputs(any(), any()))
        .thenReturn(List.of(element));

    handler.processContext(ctx);

    verify(outputProcessorFactory).getProcessor(any());
    verify(mockProcessor, never()).process(any(), any(), any());
  }

  @Test
  @DisplayName("Should skip dispatch when contract outputs list is empty")
  void shouldSkipDispatchWhenContractOutputsAreEmpty() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("{\"some_key\": \"value\"}");

    when(injectorContract.getConvertedContent()).thenReturn(mapper.createObjectNode());
    when(injectorContractContentUtils.getContractOutputs(any(), any())).thenReturn(List.of());

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isPresent());
    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should skip processor when no processor is registered for the output type")
  void shouldSkipWhenNoProcessorRegisteredForType() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("{\"cve-field\": [\"CVE-2024-1234\"]}");

    InjectorContractContentOutputElement element = new InjectorContractContentOutputElement();
    element.setField("cve-field");
    element.setType(ContractOutputType.CVE);
    element.setFindingCompatible(true);

    when(injectorContract.getConvertedContent()).thenReturn(mapper.createObjectNode());
    when(injectorContractContentUtils.getContractOutputs(any(), any()))
        .thenReturn(List.of(element));
    when(outputProcessorFactory.getProcessor(ContractOutputType.CVE)).thenReturn(Optional.empty());

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isPresent());
    verify(mockProcessor, never()).process(any(), any(), any());
  }

  @Test
  @DisplayName(
      "Should dispatch to processor with the correct node value from the structured output")
  void shouldDispatchToProcessorWithCorrectNodeValue() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("{\"cve-field\": \"CVE-2024-9999\"}");

    InjectorContractContentOutputElement element = new InjectorContractContentOutputElement();
    element.setField("cve-field");
    element.setType(ContractOutputType.CVE);
    element.setFindingCompatible(true);

    when(injectorContract.getConvertedContent()).thenReturn(mapper.createObjectNode());
    when(injectorContractContentUtils.getContractOutputs(any(), any()))
        .thenReturn(List.of(element));
    when(outputProcessorFactory.getProcessor(ContractOutputType.CVE))
        .thenReturn(Optional.of(mockProcessor));

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isPresent());
    verify(mockProcessor, times(1))
        .process(
            eq(ctx),
            argThat(c -> "cve-field".equals(c.key()) && ContractOutputType.CVE.equals(c.type())),
            argThat(node -> "CVE-2024-9999".equals(node.asText())));
  }

  private ExecutionProcessingContext createValidCtx(String json) {
    return new ExecutionProcessingContext(
        inject,
        null,
        buildInput(ExecutionTraceStatus.SUCCESS, InjectExecutionAction.complete, json),
        Map.of());
  }

  private InjectExecutionInput buildInput(
      ExecutionTraceStatus status, InjectExecutionAction action, String jsonContent) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setStatus(status.toString());
    input.setAction(action);
    input.setOutputStructured(jsonContent);
    return input;
  }
}
