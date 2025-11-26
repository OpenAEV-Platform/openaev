package io.openaev.rest.inject;

import static io.openaev.database.model.ExecutionTraceAction.EXECUTION;

import io.openaev.aop.RBAC;
import io.openaev.api.inject_result.dto.InjectResultPayloadExecutionOutput;
import io.openaev.database.model.*;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.utils.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class InjectExecutionResultService {

  private final InjectService injectService;
  private final InjectStatusService injectStatusService;

  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public InjectResultPayloadExecutionOutput injectExecutionResultPayload(
      @NotBlank final String injectId,
      @NotBlank final String targetId,
      @NotNull final TargetType targetType) {
    InjectStatus injectStatus = this.injectStatusService.findInjectStatusByInjectId(injectId);
    InjectResultPayloadExecutionOutput output = new InjectResultPayloadExecutionOutput();
    output.setPayloadCommandBlocks(
        Optional.of(injectStatus)
            .map(InjectStatus::getPayloadOutput)
            .map(StatusPayload::getPayloadCommandBlocks)
            .orElse(new ArrayList<>()));

    List<ExecutionTrace> traces =
        this.injectService.getInjectTracesFromInjectAndTarget(injectId, targetId, targetType);
    output.setTraces(traces.stream().filter(t -> EXECUTION.equals(t.getAction())).toList());

    return output;
  }
}
