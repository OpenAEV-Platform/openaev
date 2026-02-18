package io.openaev.rest.inject.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
  @DisplayName("Should support only injector execution contexts")
  void testSupports() {
    ExecutionProcessingContext injectorCtx =
        new ExecutionProcessingContext(inject, null, new InjectExecutionInput(), Map.of());

    assertTrue(handler.supports(injectorCtx));
  }

  @Test
  @DisplayName("Should return empty if status is not success or action is not COMPLETE")
  void testEarlyExitConditions() throws Exception {
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
  @DisplayName("Should skip processor if the key is missing from the structured output JSON")
  void testSkipWhenKeyIsMissing() throws Exception {
    // JSON exists but does not contain "missing_key"
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
