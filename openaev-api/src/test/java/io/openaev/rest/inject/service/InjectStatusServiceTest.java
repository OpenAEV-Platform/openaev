package io.openaev.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetAgentJobFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InjectStatusServiceTest {

  @Mock private InjectStatusRepository injectStatusRepository;

  @InjectMocks private InjectStatusService injectStatusService;

  @Nested
  @DisplayName("addJobRetrievalTraces")
  class AddJobRetrievalTraces {

    @Test
    void given_emptyJobList_should_doNothing() {
      // Arrange & Act
      injectStatusService.addJobRetrievalTraces(List.of());

      // Assert
      verifyNoInteractions(injectStatusRepository);
    }

    @Test
    void given_validJob_should_addTraceAndSaveStatus() {
      // Arrange
      Inject inject = InjectFixture.getDefaultInject();
      inject.setId("inject-1");

      InjectStatus status = InjectStatusFixture.createPendingInjectStatus();
      status.setInject(inject);

      Agent agent = AgentFixture.createDefaultAgentService();
      AssetAgentJob job = AssetAgentJobFixture.createDefaultAssetAgentJob(agent);
      job.setInject(inject);

      when(injectStatusRepository.findAllByInjectIdIn(Set.of("inject-1")))
          .thenReturn(List.of(status));

      // Act
      injectStatusService.addJobRetrievalTraces(List.of(job));

      // Assert
      assertThat(status.getTraces()).hasSize(1);
      assertThat(status.getTraces().getFirst().getAction()).isEqualTo(ExecutionTraceAction.START);
      assertThat(status.getTraces().getFirst().getStatus()).isEqualTo(ExecutionTraceStatus.INFO);
      assertThat(status.getTraces().getFirst().getAgent()).isEqualTo(agent);
      verify(injectStatusRepository).saveAll(List.of(status));
    }
  }

  @Nested
  @DisplayName("computeAgentTraceStatus")
  class ComputeAgentTraceStatus {

    @Test
    void given_emptyTraceList_should_returnNull() {
      // Arrange & Act
      ExecutionTraceStatus result = injectStatusService.computeAgentTraceStatus(List.of());

      // Assert
      assertThat(result).isNull();
    }

    @Test
    void given_onlyInfoTraces_should_returnNull() {
      // Arrange
      ExecutionTrace trace = new ExecutionTrace();
      trace.setStatus(ExecutionTraceStatus.INFO);

      // Act
      ExecutionTraceStatus result = injectStatusService.computeAgentTraceStatus(List.of(trace));

      // Assert
      assertThat(result).isNull();
    }

    @Test
    void given_singleSuccessTrace_should_returnSuccess() {
      // Arrange
      ExecutionTrace trace = new ExecutionTrace();
      trace.setStatus(ExecutionTraceStatus.SUCCESS);

      // Act
      ExecutionTraceStatus result = injectStatusService.computeAgentTraceStatus(List.of(trace));

      // Assert
      assertThat(result).isEqualTo(ExecutionTraceStatus.SUCCESS);
    }

    @Test
    void given_singleErrorTrace_should_returnThatExactErrorStatus() {
      // Arrange
      ExecutionTrace trace = new ExecutionTrace();
      trace.setStatus(ExecutionTraceStatus.TIMEOUT);

      // Act
      ExecutionTraceStatus result = injectStatusService.computeAgentTraceStatus(List.of(trace));

      // Assert
      assertThat(result).isEqualTo(ExecutionTraceStatus.TIMEOUT);
    }

    @Test
    void given_sameErrorStatusTwice_should_returnThatExactErrorStatus() {
      // Arrange
      ExecutionTrace trace1 = new ExecutionTrace();
      trace1.setStatus(ExecutionTraceStatus.COMMAND_NOT_FOUND);
      ExecutionTrace trace2 = new ExecutionTrace();
      trace2.setStatus(ExecutionTraceStatus.COMMAND_NOT_FOUND);

      // Act
      ExecutionTraceStatus result =
          injectStatusService.computeAgentTraceStatus(List.of(trace1, trace2));

      // Assert
      assertThat(result).isEqualTo(ExecutionTraceStatus.COMMAND_NOT_FOUND);
    }

    @Test
    void given_differentErrorStatuses_should_returnGenericError() {
      // Arrange
      ExecutionTrace trace1 = new ExecutionTrace();
      trace1.setStatus(ExecutionTraceStatus.TIMEOUT);
      ExecutionTrace trace2 = new ExecutionTrace();
      trace2.setStatus(ExecutionTraceStatus.COMMAND_NOT_FOUND);

      // Act
      ExecutionTraceStatus result =
          injectStatusService.computeAgentTraceStatus(List.of(trace1, trace2));

      // Assert
      assertThat(result).isEqualTo(ExecutionTraceStatus.ERROR);
    }

    @Test
    void given_errorAndSuccessTraces_should_returnError() {
      // Arrange
      ExecutionTrace errorTrace = new ExecutionTrace();
      errorTrace.setStatus(ExecutionTraceStatus.INTERRUPTED);
      ExecutionTrace successTrace = new ExecutionTrace();
      successTrace.setStatus(ExecutionTraceStatus.SUCCESS);

      // Act
      ExecutionTraceStatus result =
          injectStatusService.computeAgentTraceStatus(List.of(errorTrace, successTrace));

      // Assert
      assertThat(result).isEqualTo(ExecutionTraceStatus.INTERRUPTED);
    }

    @Test
    void given_warningTrace_should_returnSuccess() {
      // Arrange
      ExecutionTrace trace = new ExecutionTrace();
      trace.setStatus(ExecutionTraceStatus.WARNING);

      // Act
      ExecutionTraceStatus result = injectStatusService.computeAgentTraceStatus(List.of(trace));

      // Assert
      assertThat(result).isEqualTo(ExecutionTraceStatus.SUCCESS);
    }
  }

  @Test
  public void givenExecutionTraceIsCompleteError_whenComputing_thenTrace_isCompleteError() {
    // given
    Agent agent = new Agent();
    ExecutionTrace executionTrace = new ExecutionTrace();
    executionTrace.setStatus(ExecutionTraceStatus.ERROR);
    executionTrace.setAction(ExecutionTraceAction.COMPLETE);
    executionTrace.setAgent(agent);

    // traces:
    ExecutionTrace executionTrace1 = new ExecutionTrace();
    executionTrace1.setStatus(ExecutionTraceStatus.INFO);
    executionTrace1.setAction(ExecutionTraceAction.START);
    executionTrace1.setAgent(agent);
    ExecutionTrace executionTrace2 = new ExecutionTrace();
    executionTrace2.setStatus(ExecutionTraceStatus.SUCCESS);
    executionTrace2.setAction(ExecutionTraceAction.EXECUTION);
    executionTrace2.setAgent(agent);
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setTraces(List.of(executionTrace1, executionTrace2));

    // when
    injectStatusService.computeExecutionTraceStatusIfNeeded(injectStatus, executionTrace, agent);

    // then
    assertEquals(ExecutionTraceStatus.ERROR, executionTrace.getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getAction());
  }

  @Test
  public void
      givenExecutionTraceIsCompleteInfoAndNoError_whenComputing_thenTrace_isCompleteSuccess() {
    // given
    Agent agent = new Agent();
    ExecutionTrace executionTrace = new ExecutionTrace();

    // traces:
    executionTrace.setStatus(ExecutionTraceStatus.INFO);
    executionTrace.setAction(ExecutionTraceAction.START);
    executionTrace.setAgent(agent);
    ExecutionTrace executionTrace2 = new ExecutionTrace();
    executionTrace2.setStatus(ExecutionTraceStatus.SUCCESS);
    executionTrace2.setAction(ExecutionTraceAction.EXECUTION);
    executionTrace2.setAgent(agent);
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setTraces(List.of(executionTrace, executionTrace2));

    // when
    injectStatusService.computeExecutionTraceStatusIfNeeded(injectStatus, executionTrace, agent);

    // then
    assertEquals(ExecutionTraceStatus.INFO, executionTrace.getStatus());
    assertEquals(ExecutionTraceAction.START, executionTrace.getAction());
    assertEquals(ExecutionTraceStatus.SUCCESS, executionTrace2.getStatus());
    assertEquals(ExecutionTraceAction.EXECUTION, executionTrace2.getAction());
  }

  @Test
  public void givenExecutionTraceIsCompleteInfoWithError_whenComputing_thenTrace_isCompleteError() {
    // given
    Agent agent = new Agent();

    // traces:
    ExecutionTrace executionTrace1 = new ExecutionTrace();
    executionTrace1.setStatus(ExecutionTraceStatus.INFO);
    executionTrace1.setAction(ExecutionTraceAction.START);
    executionTrace1.setAgent(agent);
    ExecutionTrace executionTrace2 = new ExecutionTrace();
    executionTrace2.setStatus(ExecutionTraceStatus.ERROR);
    executionTrace2.setAction(ExecutionTraceAction.EXECUTION);
    executionTrace2.setAgent(agent);
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setTraces(List.of(executionTrace1, executionTrace2));

    // when
    injectStatusService.computeExecutionTraceStatusIfNeeded(injectStatus, executionTrace1, agent);

    // then
    assertEquals(ExecutionTraceStatus.INFO, executionTrace1.getStatus());
    assertEquals(ExecutionTraceAction.START, executionTrace1.getAction());
    assertEquals(ExecutionTraceStatus.ERROR, executionTrace2.getStatus());
    assertEquals(ExecutionTraceAction.EXECUTION, executionTrace2.getAction());
  }
}
