package io.openaev.rest.inject.service;

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

  @InjectMocks private InjectStatusService injectStatusService;

  private ExecutionTrace startTrace(InjectStatus status, Agent agent, Instant time) {
    return new ExecutionTrace(
        status, ExecutionTraceStatus.INFO, null, "start", ExecutionTraceAction.START, agent, time);
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
      @Mock private InjectService injectService;
      @Mock private InjectUtils injectUtils;
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
                injectService,
                injectUtils,
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

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(EventType.EXECUTION);
        assertThat(event.getEventScope()).isEqualTo(AuditEventScope.INJECT_STATUS_TRANSITION);
        assertThat(event.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
        assertThat(event.getOrigin()).isEqualTo(AuditEventOrigin.SCHEDULED);
        assertThat(event.getResourceId()).isEqualTo("inject-1");
        assertThat(event.getContextData().get("previous_status")).isEqualTo("PENDING");
        assertThat(event.getContextData()).containsKey("new_status");
        assertThat(event.getContextData().get("executor_type")).isEqualTo("openaev");
      }

      @Test
      @DisplayName("given_noStatusChange_should_notEmitAuditEvent")
      void given_noStatusChange_should_notEmitAuditEvent() {
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

        InjectExecutionInput input = new InjectExecutionInput();
        input.setAction(InjectExecutionAction.command_execution);
        input.setStatus("SUCCESS");
        input.setMessage("running");
        input.setDuration(0);

        when(executionTraceRepositoryHelper.saveExecutionTrace(any())).thenReturn("trace-id");

        // Act
        injectStatusService.updateInjectStatus(inject, null, input, null);

        // Assert — no audit event since status remains PENDING
        verify(auditLogger, never()).logEvent(any());
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
        injectStatusService.updateFinalInjectStatus(injectStatus);

        // Assert — status changed from PENDING to EXECUTED
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).logEvent(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(EventType.EXECUTION);
        assertThat(event.getEventScope()).isEqualTo(AuditEventScope.INJECT_STATUS_TRANSITION);
        assertThat(event.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
        assertThat(event.getOrigin()).isEqualTo(AuditEventOrigin.SCHEDULED);
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
        injectStatusService.updateFinalInjectStatus(injectStatus);

        // Assert — no event because status didn't change
        verify(auditLogger, never()).logEvent(any());
      }
    }
  }
}
