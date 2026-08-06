package io.openaev.rest.inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.api.inject_result.dto.InjectResultPayloadExecutionOutput;
import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.InjectStatus;
import io.openaev.rest.atomic_testing.form.ExecutionTraceOutput;
import io.openaev.rest.inject.service.ExecutableInjectService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.utils.TargetType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
