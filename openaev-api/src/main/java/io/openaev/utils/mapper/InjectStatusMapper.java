package io.openaev.utils.mapper;

import static io.openaev.utils.mapper.AgentMapper.toAgentOutput;

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
                    InjectStatusOutput.builder().build(),
                    status,
                    status.getTraces().stream()
                        .filter(
                            trace -> trace.getAgent() == null && trace.getIdentifiers().isEmpty())
                        .toList()))
        .orElseGet(() -> InjectStatusOutput.builder().build());
  }

  /**
   * Builds the status output with EVERY execution trace (global orchestration traces plus each
   * agent's own traces), for the complete execution log. Callers that only want the orchestration
   * traces should use {@link #toInjectStatusOutput}.
   */
  public InjectStatusOutput toInjectStatusOutputWithAllTraces(Optional<InjectStatus> injectStatus) {
    return injectStatus
        .map(
            status -> {
              InjectStatusOutput output = InjectStatusOutput.builder().build();
              output.setId(status.getId());
              output.setName(status.getName().name());
              output.setTraces(toExecutionTracesOutput(status.getTraces()));
              output.setTrackingSentDate(status.getTrackingSentDate());
              output.setTrackingEndDate(status.getTrackingEndDate());
              return output;
            })
        .orElseGet(() -> InjectStatusOutput.builder().build());
  }

  /**
   * Builds the test status output. Unlike {@link #toInjectStatusOutput}, user-scoped traces are
   * kept: a test is only sent to the current user, so the traces email and SMS injectors attach to
   * the recipient's user id are the test result itself.
   */
  public InjectTestStatusOutput toInjectTestStatusOutput(InjectTestStatus injectTestStatus) {
    InjectTestStatusOutput output = InjectTestStatusOutput.builder().build();
    buildInjectStatusOutput(
        output,
        injectTestStatus,
        injectTestStatus.getTraces().stream().filter(trace -> trace.getAgent() == null).toList());

    output.setInjectId(injectTestStatus.getInject().getId());
    output.setInjectType(injectTestStatus.getInject().getType());
    output.setInjectTitle(injectTestStatus.getInject().getTitle());

    return output;
  }

  private <T extends InjectStatusOutput> T buildInjectStatusOutput(
      T output, BaseInjectStatus status, List<ExecutionTrace> mainTraces) {
    output.setId(status.getId());
    output.setName(status.getName().name());
    output.setTraces(toExecutionTracesOutput(mainTraces));
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

  public static List<ExecutionTraceOutput> toExecutionTracesOutput(List<ExecutionTrace> traces) {
    return traces.stream()
        .map(
            trace ->
                ExecutionTraceOutput.builder()
                    .status(trace.getStatus())
                    .time(trace.getTime())
                    .message(trace.getMessage())
                    .action(trace.getAction())
                    .agent(trace.getAgent() != null ? toAgentOutput(trace.getAgent()) : null)
                    .build())
        .toList();
  }
}
