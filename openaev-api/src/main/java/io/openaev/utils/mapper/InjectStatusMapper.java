package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.rest.atomic_testing.form.*;
import io.openaev.rest.inject.output.InjectTestStatusOutput;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InjectStatusMapper {

  private final AgentMapper agentMapper;

  public InjectStatusOutput toInjectStatusOutput(Optional<InjectStatus> injectStatus) {
    return injectStatus
        .map(
            status ->
                this.<InjectStatusOutput>buildInjectStatusOutput(
                    InjectStatusOutput.builder().build(), status, status.getTraces()))
        .orElseGet(() -> InjectStatusOutput.builder().build());
  }

  public InjectTestStatusOutput toInjectTestStatusOutput(InjectTestStatus injectTestStatus) {
    InjectTestStatusOutput output = InjectTestStatusOutput.builder().build();
    buildInjectStatusOutput(output, injectTestStatus, injectTestStatus.getTraces());

    output.setInjectId(injectTestStatus.getInject().getId());
    output.setInjectType(
        injectTestStatus
            .getInject()
            .getInjectorContract()
            .map(InjectorContract::getInjector)
            .map(Injector::getType)
            .orElse(null));
    output.setInjectTitle(injectTestStatus.getInject().getTitle());

    return output;
  }

  private <T extends InjectStatusOutput> T buildInjectStatusOutput(
      T output, BaseInjectStatus status, List<ExecutionTrace> executionTraces) {
    output.setId(status.getId());
    output.setName(status.getName().name());
    output.setTraces(
        toExecutionTracesOutput(
            executionTraces.stream()
                .filter(trace -> trace.getAgent() == null && trace.getIdentifiers().isEmpty())
                .toList()));
    output.setTrackingSentDate(status.getTrackingSentDate());
    output.setTrackingEndDate(status.getTrackingEndDate());
    return output;
  }

  public InjectStatusSimple toInjectStatusSimple(Optional<InjectStatus> injectStatus) {
    return injectStatus
        .map(
            status ->
                InjectStatusSimple.builder()
                    .id(status.getId())
                    .name(status.getName().name())
                    .trackingSentDate(status.getTrackingSentDate())
                    .trackingEndDate(status.getTrackingEndDate())
                    .build())
        .orElseGet(() -> InjectStatusSimple.builder().build());
  }

  public List<ExecutionTraceOutput> toExecutionTracesOutput(List<ExecutionTrace> traces) {
    return traces.stream()
        .map(
            trace ->
                ExecutionTraceOutput.builder()
                    .status(trace.getStatus())
                    .time(trace.getTime())
                    .message(trace.getMessage())
                    .action(trace.getAction())
                    .agent(
                        trace.getAgent() != null
                            ? agentMapper.toAgentOutput(trace.getAgent())
                            : null)
                    .build())
        .toList();
  }
}
