package io.openaev.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.InjectStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inject status execution-time resolution")
class InjectStatusServiceTest {

  private static final Instant START_TIME = Instant.parse("2026-07-24T09:00:00Z");

  @InjectMocks private InjectStatusService injectStatusService;

  private ExecutionTrace startTrace(InjectStatus status, Agent agent, Instant time) {
    return new ExecutionTrace(
        status, ExecutionTraceStatus.INFO, null, "start", ExecutionTraceAction.START, agent, time);
  }

  @Test
  @DisplayName("Agent-less START traces (global distribution trace) are skipped, not NPE'd")
  void agentLessStartTracesAreSkipped() {
    InjectStatus status = new InjectStatus();
    Agent agent = new Agent();
    agent.setId("agent-1");
    // The global distribution trace has no agent and sorts first (earliest time)
    status.getTraces().add(startTrace(status, null, START_TIME.minus(1, ChronoUnit.MINUTES)));
    status.getTraces().add(startTrace(status, agent, START_TIME));

    Instant executionTime =
        injectStatusService.getExecutionTimeFromStartTraceTimeAndDurationByAgentId(
            status, "agent-1", 5000);

    assertThat(executionTime).isEqualTo(START_TIME.plusMillis(5000));
  }

  @Test
  @DisplayName("Only agent-less START traces present falls back to the current time")
  void onlyAgentLessTracesFallBackToNow() {
    InjectStatus status = new InjectStatus();
    status.getTraces().add(startTrace(status, null, START_TIME));

    Instant before = Instant.now();
    Instant executionTime =
        injectStatusService.getExecutionTimeFromStartTraceTimeAndDurationByAgentId(
            status, "agent-1", 5000);

    assertThat(executionTime).isAfterOrEqualTo(before);
  }
}
