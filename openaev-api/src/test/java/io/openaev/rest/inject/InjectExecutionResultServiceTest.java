package io.openaev.rest.inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.inject_result.dto.InjectResultPayloadExecutionOutput;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Command;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.IocValidationTestKind;
import io.openaev.database.model.PayloadCommandBlock;
import io.openaev.database.model.Tenant;
import io.openaev.rest.atomic_testing.form.ExecutionTraceOutput;
import io.openaev.rest.inject.service.ExecutableInjectService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.utils.TargetType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inject execution-result payload")
class InjectExecutionResultServiceTest {

  @Test
  @DisplayName("Missing inject status returns empty payload blocks and traces instead of 404")
  void given_missingInjectStatus_should_returnEmptyResult() {
    // Arrange
    InjectService injectService = mock(InjectService.class);
    InjectStatusService injectStatusService = mock(InjectStatusService.class);
    ExecutableInjectService executableInjectService = mock(ExecutableInjectService.class);
    InjectExecutionResultService service =
        new InjectExecutionResultService(
            injectService, injectStatusService, executableInjectService);
    String injectId = "inject-1";
    String targetId = "target-1";

    when(injectStatusService.findInjectStatusByInjectIdOptional(injectId))
        .thenReturn(Optional.empty());
    when(injectService.getInjectTracesFromInjectAndTarget(injectId, targetId, TargetType.ASSETS))
        .thenReturn(List.of());

    // Act
    InjectResultPayloadExecutionOutput output =
        service.injectExecutionResultPayload(injectId, targetId, TargetType.ASSETS);

    // Assert
    List<?> payloadCommandBlocks =
        (List<?>) ReflectionTestUtils.getField(output, "payloadCommandBlocks");
    Map<?, ?> traces = (Map<?, ?>) ReflectionTestUtils.getField(output, "traces");
    assertThat(payloadCommandBlocks).isEmpty();
    assertThat(traces).isEmpty();
  }

  @Test
  @DisplayName("Execution traces are still returned when status is not initialized yet")
  void given_missingInjectStatusAndExistingTraces_should_returnExecutionTraces() {
    // Arrange
    InjectService injectService = mock(InjectService.class);
    InjectStatusService injectStatusService = mock(InjectStatusService.class);
    ExecutableInjectService executableInjectService = mock(ExecutableInjectService.class);
    InjectExecutionResultService service =
        new InjectExecutionResultService(
            injectService, injectStatusService, executableInjectService);
    String injectId = "inject-2";
    String targetId = "target-2";

    Agent agent = new Agent();
    agent.setId("agent-1");
    ExecutionTrace trace =
        new ExecutionTrace(
            new InjectStatus(),
            ExecutionTraceStatus.EXECUTED,
            null,
            "done",
            ExecutionTraceAction.EXECUTION,
            agent,
            Instant.now());

    when(injectStatusService.findInjectStatusByInjectIdOptional(injectId))
        .thenReturn(Optional.empty());
    when(injectService.getInjectTracesFromInjectAndTarget(injectId, targetId, TargetType.ASSETS))
        .thenReturn(List.of(trace));

    // Act
    InjectResultPayloadExecutionOutput output =
        service.injectExecutionResultPayload(injectId, targetId, TargetType.ASSETS);

    // Assert
    @SuppressWarnings("unchecked")
    Map<String, List<ExecutionTraceOutput>> traces =
        (Map<String, List<ExecutionTraceOutput>>) ReflectionTestUtils.getField(output, "traces");
    assertThat(traces).containsKey("agent-1");
    assertThat(traces.get("agent-1")).hasSize(1);
  }

  @Test
  @DisplayName("The IOC validation file drop is displayed with the run directory it ran in")
  void given_iocValidationFileDrop_should_displayTheExecutedRunDirectory() {
    // Arrange
    InjectService injectService = mock(InjectService.class);
    InjectStatusService injectStatusService = mock(InjectStatusService.class);
    ExecutableInjectService executableInjectService = mock(ExecutableInjectService.class);
    InjectExecutionResultService service =
        new InjectExecutionResultService(
            injectService, injectStatusService, executableInjectService);
    Command payload = new Command();
    payload.setTenant(new Tenant("tenant-a"));
    payload.setId(
        PayloadService.iocValidationPayloadId(
            IocValidationTestKind.FILE_DROP,
            PayloadService.IOC_VALIDATION_POSIX_EXECUTOR,
            "tenant-a"));
    InjectorContract contract = new InjectorContract();
    contract.setPayload(payload);
    contract.setConvertedContent(JsonNodeFactory.instance.objectNode());
    Inject inject = new Inject();
    inject.setId("inject-3");
    inject.setInjectorContract(contract);
    ObjectNode content = JsonNodeFactory.instance.objectNode();
    content.put(PayloadService.IOC_VALIDATION_RUN_KEY, "0123456789abcdef0123456789abcdef");
    inject.setContent(content);
    when(injectService.inject("inject-3")).thenReturn(inject);
    String executedRun =
        PayloadService.iocValidationExecutionContent(content, payload, "inject-3")
            .get(PayloadService.IOC_VALIDATION_RUN_KEY)
            .asText();

    // Act
    ReflectionTestUtils.invokeMethod(
        service,
        "resolveArgumentPlaceholders",
        "inject-3",
        List.of(new PayloadCommandBlock("sh", "drop", List.of("cleanup"))));

    // Assert
    ArgumentCaptor<ObjectNode> displayed = ArgumentCaptor.forClass(ObjectNode.class);
    verify(executableInjectService, times(2))
        .resolveArgumentsForDisplay(anyString(), anyList(), anyList(), displayed.capture());
    assertThat(displayed.getAllValues())
        .allSatisfy(
            node ->
                assertThat(node.get(PayloadService.IOC_VALIDATION_RUN_KEY).asText())
                    .isEqualTo(executedRun)
                    .isNotEqualTo("0123456789abcdef0123456789abcdef"));
  }
}
