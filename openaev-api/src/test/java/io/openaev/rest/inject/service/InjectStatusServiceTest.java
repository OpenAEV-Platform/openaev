package io.openaev.rest.inject.service;

import static io.openaev.rest.inject.service.InjectStatusService.LATE_REPORT_SUFFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.database.helper.ExecutionTraceRepositoryHelper;
import io.openaev.database.model.*;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.InjectAuthorisationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.utils.InjectUtils;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inject status execution-time resolution")
class InjectStatusServiceTest {

  private static final Instant START_TIME = Instant.parse("2026-07-24T09:00:00Z");

  @Mock private InjectAuthorisationRepository injectAuthorisationRepository;

  @InjectMocks private InjectStatusService injectStatusService;

  private ExecutionTrace startTrace(InjectStatus status, Agent agent, Instant time) {
    return new ExecutionTrace(
        status, ExecutionTraceStatus.INFO, null, "start", ExecutionTraceAction.START, agent, time);
  }

  @Nested
  @DisplayName("Clean-up on terminal status")
  class CleanupOnTerminalStatusTest {

    @Test
    @DisplayName("given terminal status should delete inject authorisation")
    void given_terminal_status_should_delete_inject_authorisation() {
      // -- ARRANGE --
      Inject inject = mock(Inject.class);
      when(inject.getId()).thenReturn("inject-terminated");
      InjectStatus status = new InjectStatus();
      status.setInject(inject);
      status.setName(io.openaev.database.model.ExecutionStatus.EXECUTED);

      // -- ACT --
      injectStatusService.deleteInjectAuthorisationIfExecutionEnded(status);

      // -- ASSERT --
      verify(injectAuthorisationRepository).deleteAllByInjectId("inject-terminated");
    }

    @Test
    @DisplayName("given in-progress status should not delete inject authorisation")
    void given_in_progress_status_should_not_delete_inject_authorisation() {
      // -- ARRANGE --
      Inject inject = mock(Inject.class);
      InjectStatus status = new InjectStatus();
      status.setInject(inject);
      status.setName(io.openaev.database.model.ExecutionStatus.PENDING);

      // -- ACT --
      injectStatusService.deleteInjectAuthorisationIfExecutionEnded(status);

      // -- ASSERT --
      verify(injectAuthorisationRepository, never()).deleteAllByInjectId(any());
    }
  }

  @Nested
  @DisplayName("Audit event on status transition")
  class AuditEventOnStatusTransition {

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

    @Nested
    class InjectStatusServiceAuditTest {

      @Mock private InjectRepository injectRepository;
      @Mock private AgentRepository agentRepository;
      @Mock private InjectAgentResolverService injectAgentResolverService;
      @Mock private InjectUtils injectUtils;
      @Mock private InjectAuthorisationRepository injectAuthorisationRepository;
      @Mock private InjectStatusRepository injectStatusRepository;
      @Mock private ExecutionTraceRepositoryHelper executionTraceRepositoryHelper;
      @Mock private AuditLogger auditLogger;
      @Mock private EntityManager entityManager;
      @Mock private ObjectMapper objectMapper;
      @Mock private ApplicationEventPublisher eventPublisher;

      private InjectStatusService injectStatusService;

      @BeforeEach
      void setUp() {
        injectStatusService =
            new InjectStatusService(
                injectRepository,
                agentRepository,
                injectAgentResolverService,
                injectUtils,
                new io.openaev.service.payload.PayloadExecutionSnapshotService(
                    org.mockito.Mockito.mock(
                        io.openaev.service.payload.PayloadContentHashService.class)),
                injectAuthorisationRepository,
                injectStatusRepository,
                executionTraceRepositoryHelper,
                Optional.of(auditLogger),
                eventPublisher,
                objectMapper,
                entityManager);
      }

      @Test
      @DisplayName(
          "given_statusTransitionInUpdateInjectStatus_should_emitInjectStatusTransitionAuditEvent")
      void
          given_statusTransitionInUpdateInjectStatus_should_emitInjectStatusTransitionAuditEvent() {
        // Arrange
        Inject inject = new Inject();
        inject.setId("inject-1");
        inject.setTitle("Test Inject");

        InjectStatus injectStatus = new InjectStatus();
        injectStatus.setId("status-1");
        injectStatus.setName(ExecutionStatus.PENDING);
        injectStatus.setInject(inject);
        injectStatus.setTraces(new ArrayList<>());
        injectStatus.setExpectedAgentCount(1);
        inject.setStatus(injectStatus);

        Agent agent = new Agent();
        agent.setId("agent-1");
        Executor executor = new Executor();
        executor.setType("openaev");
        agent.setExecutor(executor);

        InjectExecutionInput input = new InjectExecutionInput();
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("SUCCESS");
        input.setMessage("done");
        input.setDuration(0);

        when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-id");
        when(injectRepository.updateUpdatedAt(any(), any())).thenReturn(1);

        // Act
        injectStatusService.updateInjectStatus(inject, agent, input, null);

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger, atLeastOnce()).logEvent(eventCaptor.capture());

        // The final transition is logged once, with the agent that completed the inject
        assertThat(eventCaptor.getAllValues())
            .filteredOn(e -> AuditEventScope.INJECT_STATUS_TRANSITION.equals(e.getEventScope()))
            .hasSize(1);
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(EventType.EXECUTION);
        assertThat(event.getEventScope()).isEqualTo(AuditEventScope.INJECT_STATUS_TRANSITION);
        assertThat(event.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
        assertThat(event.getOrigin()).isEqualTo(AuditEventOrigin.SYSTEM);
        assertThat(event.getResourceId()).isEqualTo("inject-1");
        assertThat(event.getContextData().get("previous_status")).isEqualTo("PENDING");
        assertThat(event.getContextData()).containsKey("new_status");
        assertThat(event.getContextData().get("executor_type")).isEqualTo("openaev");
      }

      @Test
      @DisplayName("given_traceCreationWithoutStatusChange_should_emitAgentTraceStepAuditEvent")
      void given_traceCreationWithoutStatusChange_should_emitAgentTraceStepAuditEvent() {
        // Arrange — a non-complete trace that doesn't change the status
        Inject inject = new Inject();
        inject.setId("inject-2");
        inject.setTitle("Test Inject 2");

        InjectStatus injectStatus = new InjectStatus();
        injectStatus.setId("status-2");
        injectStatus.setName(ExecutionStatus.PENDING);
        injectStatus.setInject(inject);
        injectStatus.setTraces(new ArrayList<>());
        inject.setStatus(injectStatus);

        Agent agent = new Agent();
        agent.setId("agent-2");
        Asset endpoint = new Asset();
        endpoint.setId("asset-2");
        agent.setAsset(endpoint);

        InjectExecutionInput input = new InjectExecutionInput();
        input.setAction(InjectExecutionAction.command_execution);
        input.setStatus("SUCCESS");
        input.setMessage("running");
        input.setDuration(0);

        when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-id");

        // Act
        injectStatusService.updateInjectStatus(inject, agent, input, null);

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).logEvent(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(EventType.EXECUTION);
        assertThat(event.getEventScope()).isEqualTo(AuditEventScope.AGENT_TRACE_STEP);
        assertThat(event.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
        assertThat(event.getOrigin()).isEqualTo(AuditEventOrigin.SYSTEM);
        assertThat(event.getContextData().get("trace_id")).isEqualTo("trace-id");
        assertThat(event.getContextData().get("step_name")).isEqualTo("EXECUTION");
        assertThat(event.getContextData().get("trace_status")).isEqualTo("EXECUTED");
        assertThat(event.getContextData().get("inject_id")).isEqualTo("inject-2");
        assertThat(event.getContextData().get("agent_id")).isEqualTo("agent-2");
        assertThat(event.getContextData().get("endpoint_id")).isEqualTo("asset-2");
      }

      @Test
      @DisplayName("given_prerequisiteTrace_should_emitPrerequisiteStepAuditEvent")
      void given_prerequisiteTrace_should_emitPrerequisiteStepAuditEvent() {
        // Arrange
        Inject inject = new Inject();
        inject.setId("inject-prereq");
        inject.setTitle("Prerequisite Inject");

        InjectStatus injectStatus = new InjectStatus();
        injectStatus.setId("status-prereq");
        injectStatus.setName(ExecutionStatus.PENDING);
        injectStatus.setInject(inject);
        injectStatus.setTraces(new ArrayList<>());
        inject.setStatus(injectStatus);

        Agent agent = new Agent();
        agent.setId("agent-prereq");
        Asset endpoint = new Asset();
        endpoint.setId("asset-prereq");
        agent.setAsset(endpoint);

        InjectExecutionInput input = new InjectExecutionInput();
        input.setAction(InjectExecutionAction.prerequisite_check);
        input.setStatus("SUCCESS");
        input.setMessage("prerequisite validated");
        input.setDuration(0);

        when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-prereq");

        // Act
        injectStatusService.updateInjectStatus(inject, agent, input, null);

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).logEvent(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getEventScope()).isEqualTo(AuditEventScope.AGENT_TRACE_STEP);
        assertThat(event.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
        assertThat(event.getOrigin()).isEqualTo(AuditEventOrigin.SYSTEM);
        assertThat(event.getContextData().get("step_name")).isEqualTo("PREREQUISITE_CHECK");
        assertThat(event.getContextData().get("trace_status")).isEqualTo("EXECUTED");
        assertThat(event.getContextData().get("trace_id")).isEqualTo("trace-prereq");
      }

      @Test
      @DisplayName("given_cleanupTrace_should_emitCleanupStepAuditEvent")
      void given_cleanupTrace_should_emitCleanupStepAuditEvent() {
        // Arrange
        Inject inject = new Inject();
        inject.setId("inject-cleanup");
        inject.setTitle("Cleanup Inject");

        InjectStatus injectStatus = new InjectStatus();
        injectStatus.setId("status-cleanup");
        injectStatus.setName(ExecutionStatus.PENDING);
        injectStatus.setInject(inject);
        injectStatus.setTraces(new ArrayList<>());
        inject.setStatus(injectStatus);

        Agent agent = new Agent();
        agent.setId("agent-cleanup");
        Asset endpoint = new Asset();
        endpoint.setId("asset-cleanup");
        agent.setAsset(endpoint);

        InjectExecutionInput input = new InjectExecutionInput();
        input.setAction(InjectExecutionAction.cleanup_execution);
        input.setStatus("SUCCESS");
        input.setMessage("cleanup done");
        input.setDuration(0);

        when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-cleanup");

        // Act
        injectStatusService.updateInjectStatus(inject, agent, input, null);

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).logEvent(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getEventScope()).isEqualTo(AuditEventScope.AGENT_TRACE_STEP);
        assertThat(event.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
        assertThat(event.getOrigin()).isEqualTo(AuditEventOrigin.SYSTEM);
        assertThat(event.getContextData().get("step_name")).isEqualTo("CLEANUP_EXECUTION");
        assertThat(event.getContextData().get("trace_status")).isEqualTo("EXECUTED");
        assertThat(event.getContextData().get("trace_id")).isEqualTo("trace-cleanup");
      }

      @Test
      @DisplayName("given_noStatusChange_should_notEmitInjectStatusTransitionAuditEvent")
      void given_noStatusChange_should_notEmitInjectStatusTransitionAuditEvent() {
        // Arrange — a non-complete trace that keeps inject status unchanged
        Inject inject = new Inject();
        inject.setId("inject-2b");
        inject.setTitle("Test Inject 2b");

        InjectStatus injectStatus = new InjectStatus();
        injectStatus.setId("status-2b");
        injectStatus.setName(ExecutionStatus.PENDING);
        injectStatus.setInject(inject);
        injectStatus.setTraces(new ArrayList<>());
        inject.setStatus(injectStatus);

        InjectExecutionInput input = new InjectExecutionInput();
        input.setAction(InjectExecutionAction.command_execution);
        input.setStatus("SUCCESS");
        input.setMessage("running");
        input.setDuration(0);

        when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-id-2b");

        // Act
        injectStatusService.updateInjectStatus(inject, null, input, null);

        // Assert — trace event is emitted, but no status-transition event should exist
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).logEvent(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getEventScope()).isEqualTo(AuditEventScope.AGENT_TRACE_STEP);
        assertThat(event.getContextData().get("trace_id")).isEqualTo("trace-id-2b");
      }

      @Test
      @DisplayName("given_statusTransitionInUpdateFinalInjectStatus_should_emitAuditEvent")
      void given_statusTransitionInUpdateFinalInjectStatus_should_emitAuditEvent() {
        // Arrange
        Inject inject = new Inject();
        inject.setId("inject-3");
        inject.setTitle("Final Status Inject");

        InjectStatus injectStatus = new InjectStatus();
        injectStatus.setId("status-3");
        injectStatus.setName(ExecutionStatus.PENDING);
        injectStatus.setInject(inject);
        inject.setStatus(injectStatus);

        // Add a COMPLETE trace with EXECUTED status to force final status computation
        ExecutionTrace completeTrace = new ExecutionTrace();
        completeTrace.setAction(ExecutionTraceAction.COMPLETE);
        completeTrace.setStatus(ExecutionTraceStatus.EXECUTED);
        injectStatus.setTraces(new ArrayList<>());
        injectStatus.getTraces().add(completeTrace);

        // Act
        injectStatusService.updateFinalInjectStatus(injectStatus, null);

        // Assert — status changed from PENDING to EXECUTED
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).logEvent(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(EventType.EXECUTION);
        assertThat(event.getEventScope()).isEqualTo(AuditEventScope.INJECT_STATUS_TRANSITION);
        assertThat(event.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
        assertThat(event.getOrigin()).isEqualTo(AuditEventOrigin.SYSTEM);
        assertThat(event.getResourceId()).isEqualTo("inject-3");
        assertThat(event.getContextData().get("previous_status")).isEqualTo("PENDING");
        assertThat(event.getContextData().get("new_status")).isEqualTo("EXECUTED");
      }

      @Test
      @DisplayName("given_noStatusChangeInUpdateFinalInjectStatus_should_notEmitAuditEvent")
      void given_noStatusChangeInUpdateFinalInjectStatus_should_notEmitAuditEvent() {
        // Arrange — already EXECUTED, stays EXECUTED
        Inject inject = new Inject();
        inject.setId("inject-4");
        inject.setTitle("No Change Inject");

        InjectStatus injectStatus = new InjectStatus();
        injectStatus.setId("status-4");
        injectStatus.setName(ExecutionStatus.EXECUTED);
        injectStatus.setInject(inject);
        inject.setStatus(injectStatus);

        ExecutionTrace completeTrace = new ExecutionTrace();
        completeTrace.setAction(ExecutionTraceAction.COMPLETE);
        completeTrace.setStatus(ExecutionTraceStatus.EXECUTED);
        injectStatus.setTraces(new ArrayList<>());
        injectStatus.getTraces().add(completeTrace);

        // Act
        injectStatusService.updateFinalInjectStatus(injectStatus, null);

        // Assert — no event because status didn't change
        verify(auditLogger, never()).logEvent(any());
      }
    }
  }

  @Nested
  @DisplayName("Late COMPLETE callback on a closed inject")
  class LateCompleteCallbackTest {

    @Mock private InjectRepository injectRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private InjectAgentResolverService injectAgentResolverService;
    @Mock private InjectUtils injectUtils;
    @Mock private InjectAuthorisationRepository injectAuthorisationRepository;
    @Mock private InjectStatusRepository injectStatusRepository;
    @Mock private ExecutionTraceRepositoryHelper executionTraceRepositoryHelper;
    @Mock private AuditLogger auditLogger;
    @Mock private EntityManager entityManager;
    @Mock private ObjectMapper objectMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    private InjectStatusService injectStatusService;

    @BeforeEach
    void setUp() {
      injectStatusService =
          new InjectStatusService(
              injectRepository,
              agentRepository,
              injectAgentResolverService,
              injectUtils,
              injectAuthorisationRepository,
              injectStatusRepository,
              executionTraceRepositoryHelper,
              Optional.of(auditLogger),
              eventPublisher,
              objectMapper,
              entityManager);
    }

    private Agent agent(String id) {
      Agent agent = new Agent();
      agent.setId(id);
      return agent;
    }

    private InjectStatus closedStatus(ExecutionStatus name, int expectedAgentCount) {
      Inject inject = new Inject();
      inject.setId("inject-late");
      inject.setTitle("Late inject");
      InjectStatus injectStatus = new InjectStatus();
      injectStatus.setId("status-late");
      injectStatus.setName(name);
      injectStatus.setInject(inject);
      injectStatus.setTraces(new ArrayList<>());
      injectStatus.setExpectedAgentCount(expectedAgentCount);
      inject.setStatus(injectStatus);
      return injectStatus;
    }

    private ExecutionTrace completeTrace(
        InjectStatus status, Agent agent, ExecutionTraceStatus traceStatus, String message) {
      ExecutionTrace trace =
          new ExecutionTrace(
              status,
              traceStatus,
              null,
              message,
              ExecutionTraceAction.COMPLETE,
              agent,
              Instant.now().minus(10, ChronoUnit.MINUTES));
      status.getTraces().add(trace);
      return trace;
    }

    private InjectExecutionInput input(InjectExecutionAction action, String status) {
      InjectExecutionInput input = new InjectExecutionInput();
      input.setAction(action);
      input.setStatus(status);
      input.setMessage("late report");
      input.setDuration(0);
      return input;
    }

    // A late trace never changes the status: nothing is recomputed, written back nor streamed
    private void verifyStatusNotRecomputed() {
      verify(executionTraceRepositoryHelper, never()).updateInjectStatus(any(), any(), any());
      verify(injectRepository, never()).updateUpdatedAt(any(), any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    // The late trace is stored on its own with the reported message, never as a second COMPLETE
    private void assertLateTrace(ExecutionTrace trace, ExecutionTraceStatus expectedStatus) {
      assertThat(trace.getStatus()).isEqualTo(expectedStatus);
      assertThat(trace.getAction()).isEqualTo(ExecutionTraceAction.EXECUTION);
      assertThat(trace.getMessage()).isEqualTo("late report");
    }

    private String flagged(String timeoutMessage) {
      return timeoutMessage + " " + LATE_REPORT_SUFFIX;
    }

    @Test
    @DisplayName("given_lateSuccessAfterTimeout_should_keepErrorAndTimeoutTraceAndSaveLateApart")
    void given_lateSuccessAfterTimeout_should_keepErrorAndTimeoutTraceAndSaveLateApart() {
      // Arrange
      Agent agent = agent("agent-1");
      InjectStatus injectStatus = closedStatus(ExecutionStatus.ERROR, 1);
      completeTrace(injectStatus, agent, ExecutionTraceStatus.TIMEOUT, "Agent did not respond.");
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-late");

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(), agent, input(InjectExecutionAction.complete, "SUCCESS"), null);

      // Assert
      assertThat(injectStatus.getName()).isEqualTo(ExecutionStatus.ERROR);
      assertThat(injectStatus.getTraces()).hasSize(2);
      ExecutionTrace timeout = injectStatus.getTraces().getFirst();
      assertThat(timeout.getStatus()).isEqualTo(ExecutionTraceStatus.TIMEOUT);
      assertThat(timeout.getAction()).isEqualTo(ExecutionTraceAction.COMPLETE);
      assertThat(timeout.getMessage()).isEqualTo(flagged("Agent did not respond."));
      ExecutionTrace late = injectStatus.getTraces().getLast();
      assertLateTrace(late, ExecutionTraceStatus.EXECUTED);
      assertThat(late.getId()).isEqualTo("trace-late");
      verify(entityManager).merge(injectStatus);
      verifyStatusNotRecomputed();
    }

    @Test
    @DisplayName("given_lateSuccessForOneAgentOfTwo_should_keepPartial")
    void given_lateSuccessForOneAgentOfTwo_should_keepPartial() {
      // Arrange
      Agent agentA = agent("agent-a");
      Agent agentB = agent("agent-b");
      InjectStatus injectStatus = closedStatus(ExecutionStatus.PARTIAL, 2);
      completeTrace(injectStatus, agentA, ExecutionTraceStatus.EXECUTED, "done");
      completeTrace(injectStatus, agentB, ExecutionTraceStatus.TIMEOUT, "Agent B timed out.");
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-late");

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(), agentB, input(InjectExecutionAction.complete, "SUCCESS"), null);

      // Assert
      assertThat(injectStatus.getName()).isEqualTo(ExecutionStatus.PARTIAL);
      assertThat(injectStatus.getTraces())
          .extracting(ExecutionTrace::getStatus)
          .containsExactly(
              ExecutionTraceStatus.EXECUTED,
              ExecutionTraceStatus.TIMEOUT,
              ExecutionTraceStatus.EXECUTED);
      assertThat(injectStatus.getTraces())
          .filteredOn(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()))
          .extracting(ExecutionTrace::getMessage)
          .containsExactly("done", flagged("Agent B timed out."));
      assertLateTrace(injectStatus.getTraces().getLast(), ExecutionTraceStatus.EXECUTED);
      verifyStatusNotRecomputed();
    }

    @Test
    @DisplayName("given_lateInfoCompleteAfterTimeout_should_notResolveItsStatus")
    void given_lateInfoCompleteAfterTimeout_should_notResolveItsStatus() {
      // Arrange
      Agent agent = agent("agent-1");
      InjectStatus injectStatus = closedStatus(ExecutionStatus.ERROR, 1);
      completeTrace(injectStatus, agent, ExecutionTraceStatus.TIMEOUT, "Agent did not respond.");
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-late");

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(), agent, input(InjectExecutionAction.complete, "INFO"), null);

      // Assert
      assertLateTrace(injectStatus.getTraces().getLast(), ExecutionTraceStatus.INFO);
      verifyStatusNotRecomputed();
    }

    @Test
    @DisplayName("given_secondCompleteOfAnAgentWhileInjectPending_should_keepItsFirstComplete")
    void given_secondCompleteOfAnAgentWhileInjectPending_should_keepItsFirstComplete() {
      // Arrange
      Agent agentA = agent("agent-a");
      Agent agentB = agent("agent-b");
      InjectStatus injectStatus = closedStatus(ExecutionStatus.PENDING, 2);
      completeTrace(injectStatus, agentA, ExecutionTraceStatus.ERROR, "Agent A failed.");
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-id");

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(), agentA, input(InjectExecutionAction.complete, "SUCCESS"), null);

      // Assert
      assertThat(injectStatus.getName()).isEqualTo(ExecutionStatus.PENDING);
      assertThat(injectStatus.getTraces().getFirst().getMessage()).isEqualTo("Agent A failed.");
      assertLateTrace(injectStatus.getTraces().getLast(), ExecutionTraceStatus.EXECUTED);
      verifyStatusNotRecomputed();

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(), agentB, input(InjectExecutionAction.complete, "ERROR"), null);

      // Assert: the late success of agent A is not counted, otherwise the inject would be PARTIAL
      assertThat(injectStatus.getName()).isEqualTo(ExecutionStatus.ERROR);
      assertThat(injectStatus.getTraces())
          .filteredOn(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()))
          .extracting(ExecutionTrace::getStatus)
          .containsExactly(ExecutionTraceStatus.ERROR, ExecutionTraceStatus.ERROR);
    }

    @Test
    @DisplayName("given_lateCompleteWithoutTimeoutTrace_should_saveItAndKeepStatus")
    void given_lateCompleteWithoutTimeoutTrace_should_saveItAndKeepStatus() {
      // Arrange
      Agent agent = agent("agent-1");
      InjectStatus injectStatus = closedStatus(ExecutionStatus.EXECUTED, 1);
      completeTrace(injectStatus, agent, ExecutionTraceStatus.EXECUTED, "done");
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-late");

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(), agent, input(InjectExecutionAction.complete, "ERROR"), null);

      // Assert
      assertThat(injectStatus.getName()).isEqualTo(ExecutionStatus.EXECUTED);
      assertThat(injectStatus.getTraces()).hasSize(2);
      assertThat(injectStatus.getTraces().getFirst().getStatus())
          .isEqualTo(ExecutionTraceStatus.EXECUTED);
      assertThat(injectStatus.getTraces().getFirst().getMessage()).isEqualTo("done");
      ExecutionTrace late = injectStatus.getTraces().getLast();
      assertLateTrace(late, ExecutionTraceStatus.ERROR);
      assertThat(late.getId()).isEqualTo("trace-late");
      verifyStatusNotRecomputed();
    }

    @Test
    @DisplayName("given_lateNonCompleteTrace_should_saveItAndCompleteTheTimeoutMessage")
    void given_lateNonCompleteTrace_should_saveItAndCompleteTheTimeoutMessage() {
      // Arrange
      Agent agent = agent("agent-1");
      InjectStatus injectStatus = closedStatus(ExecutionStatus.ERROR, 1);
      completeTrace(injectStatus, agent, ExecutionTraceStatus.TIMEOUT, "Agent did not respond.");
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-2");

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(),
          agent,
          input(InjectExecutionAction.command_execution, "SUCCESS"),
          null);

      // Assert
      assertThat(injectStatus.getName()).isEqualTo(ExecutionStatus.ERROR);
      assertThat(injectStatus.getTraces()).hasSize(2);
      assertThat(injectStatus.getTraces().getFirst().getStatus())
          .isEqualTo(ExecutionTraceStatus.TIMEOUT);
      assertThat(injectStatus.getTraces().getFirst().getMessage())
          .isEqualTo(flagged("Agent did not respond."));
      ExecutionTrace late = injectStatus.getTraces().getLast();
      assertLateTrace(late, ExecutionTraceStatus.EXECUTED);
      verify(executionTraceRepositoryHelper).saveExecutionTrace(late);
      verifyStatusNotRecomputed();
    }

    @Test
    @DisplayName("given_agentReportAfterSimulationTimeout_should_saveItAndKeepError")
    void given_agentReportAfterSimulationTimeout_should_saveItAndKeepError() {
      // Arrange
      Agent agent = agent("agent-1");
      InjectStatus injectStatus = closedStatus(ExecutionStatus.ERROR, 1);
      completeTrace(
          injectStatus,
          null,
          ExecutionTraceStatus.TIMEOUT,
          "Inject stopped due to simulation timeout.");
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-late");

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(), agent, input(InjectExecutionAction.complete, "SUCCESS"), null);

      // Assert
      assertThat(injectStatus.getName()).isEqualTo(ExecutionStatus.ERROR);
      assertThat(injectStatus.getTraces()).hasSize(2);
      // The inject's TIMEOUT is not the agent's one: its message is left as is
      assertThat(injectStatus.getTraces().getFirst().getMessage())
          .isEqualTo("Inject stopped due to simulation timeout.");
      assertLateTrace(injectStatus.getTraces().getLast(), ExecutionTraceStatus.EXECUTED);
      verifyStatusNotRecomputed();
    }

    @Test
    @DisplayName("given_agentCompletedBeforeSimulationTimeout_should_keepEveryCompleteMessage")
    void given_agentCompletedBeforeSimulationTimeout_should_keepEveryCompleteMessage() {
      // Arrange
      Agent agent = agent("agent-1");
      InjectStatus injectStatus = closedStatus(ExecutionStatus.ERROR, 1);
      completeTrace(injectStatus, agent, ExecutionTraceStatus.EXECUTED, "done");
      completeTrace(
          injectStatus,
          null,
          ExecutionTraceStatus.TIMEOUT,
          "Inject stopped due to simulation timeout.");
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-late");

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(),
          agent,
          input(InjectExecutionAction.command_execution, "SUCCESS"),
          null);

      // Assert
      assertThat(injectStatus.getTraces())
          .filteredOn(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()))
          .extracting(ExecutionTrace::getMessage)
          .containsExactly("done", "Inject stopped due to simulation timeout.");
      assertLateTrace(injectStatus.getTraces().getLast(), ExecutionTraceStatus.EXECUTED);
      verifyStatusNotRecomputed();
    }

    @Test
    @DisplayName("given_severalLateTracesAfterTimeout_should_completeTheTimeoutMessageOnce")
    void given_severalLateTracesAfterTimeout_should_completeTheTimeoutMessageOnce() {
      // Arrange
      Agent agent = agent("agent-1");
      InjectStatus injectStatus = closedStatus(ExecutionStatus.ERROR, 1);
      ExecutionTrace timeout =
          completeTrace(
              injectStatus, agent, ExecutionTraceStatus.TIMEOUT, "Agent did not respond.");
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-late");

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(),
          agent,
          input(InjectExecutionAction.command_execution, "SUCCESS"),
          null);
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(), agent, input(InjectExecutionAction.complete, "SUCCESS"), null);

      // Assert
      assertThat(timeout.getMessage()).isEqualTo(flagged("Agent did not respond."));
      assertThat(injectStatus.getTraces()).hasSize(3);
      verifyStatusNotRecomputed();
    }

    @Test
    @DisplayName("given_agentLessTraceAfterInjectTimeout_should_completeTheInjectTimeoutMessage")
    void given_agentLessTraceAfterInjectTimeout_should_completeTheInjectTimeoutMessage() {
      // Arrange
      InjectStatus injectStatus = closedStatus(ExecutionStatus.ERROR, 0);
      completeTrace(
          injectStatus,
          null,
          ExecutionTraceStatus.TIMEOUT,
          "The inject did not complete within the 10 minutes threshold.");
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-late");

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(), null, input(InjectExecutionAction.complete, "SUCCESS"), null);

      // Assert
      assertThat(injectStatus.getName()).isEqualTo(ExecutionStatus.ERROR);
      assertThat(injectStatus.getTraces().getFirst().getMessage())
          .isEqualTo(flagged("The inject did not complete within the 10 minutes threshold."));
      assertLateTrace(injectStatus.getTraces().getLast(), ExecutionTraceStatus.EXECUTED);
      verifyStatusNotRecomputed();
    }

    @Test
    @DisplayName("given_agentLessTraceAfterEveryAgentTimedOut_should_keepAgentTimeoutMessages")
    void given_agentLessTraceAfterEveryAgentTimedOut_should_keepAgentTimeoutMessages() {
      // Arrange
      Agent agentA = agent("agent-a");
      Agent agentB = agent("agent-b");
      InjectStatus injectStatus = closedStatus(ExecutionStatus.ERROR, 2);
      completeTrace(injectStatus, agentA, ExecutionTraceStatus.TIMEOUT, "Agent A timed out.");
      completeTrace(injectStatus, agentB, ExecutionTraceStatus.TIMEOUT, "Agent B timed out.");
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-late");

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(), null, input(InjectExecutionAction.complete, "SUCCESS"), null);

      // Assert
      assertThat(injectStatus.getName()).isEqualTo(ExecutionStatus.ERROR);
      // The injector reported, not the agents: their TIMEOUT messages stay true
      assertThat(injectStatus.getTraces())
          .filteredOn(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()))
          .extracting(ExecutionTrace::getMessage)
          .containsExactly("Agent A timed out.", "Agent B timed out.");
      assertLateTrace(injectStatus.getTraces().getLast(), ExecutionTraceStatus.EXECUTED);
      verifyStatusNotRecomputed();
    }

    @Test
    @DisplayName("given_agentReportWhileInjectStillInProgress_should_beProcessedNormally")
    void given_agentReportWhileInjectStillInProgress_should_beProcessedNormally() {
      // Arrange
      Agent agent = agent("agent-1");
      InjectStatus injectStatus = closedStatus(ExecutionStatus.PENDING, 1);
      when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-1");
      when(injectRepository.updateUpdatedAt(any(), any())).thenReturn(1);

      // Act
      injectStatusService.updateInjectStatus(
          injectStatus.getInject(), agent, input(InjectExecutionAction.complete, "SUCCESS"), null);

      // Assert
      assertThat(injectStatus.getName()).isEqualTo(ExecutionStatus.EXECUTED);
      verify(executionTraceRepositoryHelper).updateInjectStatus(any(), eq("EXECUTED"), any());
    }
  }
}
