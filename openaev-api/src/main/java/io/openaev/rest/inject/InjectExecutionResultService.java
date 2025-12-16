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

  private final InjectService injectService;
  private final InjectStatusService injectStatusService;

  public InjectResultPayloadExecutionOutput injectExecutionResultPayload(
      @NotBlank final String injectId,
      @NotBlank final String targetId,
      @NotNull final TargetType targetType) {
    InjectStatus injectStatus = this.injectStatusService.findInjectStatusByInjectId(injectId);
    InjectResultPayloadExecutionOutputBuilder output =
        InjectResultPayloadExecutionOutput.builder()
            .payloadCommandBlocks(
                Optional.of(injectStatus)
                    .map(InjectStatus::getPayloadOutput)
                    .map(StatusPayload::getPayloadCommandBlocks)
                    .orElse(new ArrayList<>()));

    // group traces by agent
    List<ExecutionTrace> traces =
        injectService.getInjectTracesFromInjectAndTarget(injectId, targetId, targetType);

    Set<String> agentIds =
        traces.stream().map(ExecutionTrace::getAgent).map(Agent::getId).collect(toSet());

    Map<String, List<ExecutionTraceOutput>> executionByAgent =
        toExecutionTracesOutput(
                traces.stream().filter(t -> EXECUTION.equals(t.getAction())).toList())
            .stream()
            .collect(groupingBy(t -> t.getAgent().getId()));

    Map<String, List<ExecutionTraceOutput>> result = new LinkedHashMap<>();

    agentIds.forEach(
        agentId ->
            result.put(
                agentId, new ArrayList<>(executionByAgent.getOrDefault(agentId, List.of()))));

    output.traces(result);
    return output.build();
  }
}
