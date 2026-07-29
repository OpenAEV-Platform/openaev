package io.openaev.rest.inject;

import static io.openaev.database.model.ExecutionTraceAction.EXECUTION;
import static io.openaev.utils.mapper.InjectStatusMapper.toExecutionTracesOutput;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import io.openaev.api.inject_result.dto.InjectResultPayloadExecutionOutput;
import io.openaev.api.inject_result.dto.InjectResultPayloadExecutionOutput.InjectResultPayloadExecutionOutputBuilder;
import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.StatusPayload;
import io.openaev.rest.atomic_testing.form.ExecutionTraceOutput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.utils.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class InjectExecutionResultService {

  // Synthetic grouping key for traces produced by agentless executions (no agent attached, e.g. an
  // injector scanning a non-endpoint asset).
  static final String AGENTLESS_TRACE_KEY = "agentless";

  private final InjectService injectService;
  private final InjectStatusService injectStatusService;

  public InjectResultPayloadExecutionOutput injectExecutionResultPayload(
      @NotBlank final String injectId,
      @NotBlank final String targetId,
      @NotNull final TargetType targetType) {
    Optional<InjectStatus> injectStatus =
        this.injectStatusService.findInjectStatusByInjectIdOptional(injectId);
    InjectResultPayloadExecutionOutputBuilder output =
        InjectResultPayloadExecutionOutput.builder()
            .payloadCommandBlocks(
                injectStatus
                    .map(InjectStatus::getPayloadOutput)
                    .map(StatusPayload::getPayloadCommandBlocks)
                    .orElse(new ArrayList<>()));

    // Group execution traces per target key. Agent-based executions are keyed by agent id;
    // agentless
    // executions (e.g. an injector scanning a non-endpoint asset such as a web application) carry
    // no
    // agent, so they are bucketed under a single synthetic key. Keying on the agent id directly
    // used
    // to throw a NullPointerException for agentless traces and also dropped them from the response,
    // leaving the terminal view empty.
    List<ExecutionTrace> traces =
        injectService.getInjectTracesFromInjectAndTarget(injectId, targetId, targetType);

    Set<String> targetKeys =
        traces.stream().map(InjectExecutionResultService::traceKey).collect(toSet());

    Map<String, List<ExecutionTraceOutput>> executionByKey =
        toExecutionTracesOutput(
                traces.stream().filter(t -> EXECUTION.equals(t.getAction())).toList())
            .stream()
            .collect(
                groupingBy(t -> t.getAgent() != null ? t.getAgent().getId() : AGENTLESS_TRACE_KEY));

    Map<String, List<ExecutionTraceOutput>> result = new LinkedHashMap<>();

    targetKeys.forEach(
        key -> result.put(key, new ArrayList<>(executionByKey.getOrDefault(key, List.of()))));

    output.traces(result);
    return output.build();
  }

  private static String traceKey(final ExecutionTrace trace) {
    Agent agent = trace.getAgent();
    return agent != null ? agent.getId() : AGENTLESS_TRACE_KEY;
  }
}
