package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AgentStatus;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.EventType;
import io.openaev.database.repository.AgentRepository;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentInactivityMonitorJob unit tests")
class AgentInactivityMonitorJobTest {

  @Mock private AgentRepository agentRepository;
  @Mock private TenantScopedTransaction tenantTx;
  @Mock private AuditLogger auditLogger;

  private AgentInactivityMonitorJob createJob() {
    return new AgentInactivityMonitorJob(
        agentRepository, tenantTx, java.util.Optional.of(auditLogger));
  }

  @Nested
  @DisplayName("execute")
  class Execute {

    @Test
    void given_noStaleAgents_should_notSaveOrLogCoverageGap() {
      // Arrange
      AgentInactivityMonitorJob job = createJob();
      doAnswer(
              invocation -> {
                Consumer<String> consumer = invocation.getArgument(0);
                consumer.accept("tenant-a");
                return null;
              })
          .when(tenantTx)
          .forEachTenant(any());
      when(agentRepository.findStaleAgentsByStatus(any(Instant.class), eq(AgentStatus.ACTIVE)))
          .thenReturn(List.of());

      // Act
      job.execute(null);

      // Assert
      verify(agentRepository, never()).saveAll(any());
      verifyNoInteractions(auditLogger);
    }

    @Test
    void given_staleActiveAgent_should_markInactiveSaveAndEmitCoverageGap() {
      // Arrange
      AgentInactivityMonitorJob job = createJob();
      doAnswer(
              invocation -> {
                Consumer<String> consumer = invocation.getArgument(0);
                consumer.accept("tenant-a");
                return null;
              })
          .when(tenantTx)
          .forEachTenant(any());

      Agent staleAgent = new Agent();
      staleAgent.setId("agent-1");
      staleAgent.setLastSeen(Instant.now().minusSeconds(7_200));

      when(agentRepository.findStaleAgentsByStatus(any(Instant.class), eq(AgentStatus.ACTIVE)))
          .thenReturn(List.of(staleAgent));

      // Act
      job.execute(null);

      // Assert
      assertThat(staleAgent.getStatus()).isEqualTo(AgentStatus.INACTIVE);
      verify(agentRepository).saveAll(List.of(staleAgent));

      ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
      verify(auditLogger).logEvent(eventCaptor.capture());
      AuditEvent event = eventCaptor.getValue();
      assertThat(event.getEventType()).isEqualTo(EventType.EXECUTION);
      assertThat(event.getEventScope()).isEqualTo(AuditEventScope.COVERAGE_GAP);
      assertThat(event.getEventStatus()).isEqualTo(EventStatus.WARNING);
      assertThat(event.getResourceId()).isEqualTo("agent-1");
      assertThat(event.getContextData())
          .containsEntry("previous_status", "ACTIVE")
          .containsEntry("new_status", "INACTIVE");
    }

    @Test
    void given_alreadyInactivePopulation_should_notEmitDuplicateCoverageGap() {
      // Arrange
      AgentInactivityMonitorJob job = createJob();
      doAnswer(
              invocation -> {
                Consumer<String> consumer = invocation.getArgument(0);
                consumer.accept("tenant-a");
                return null;
              })
          .when(tenantTx)
          .forEachTenant(any());
      when(agentRepository.findStaleAgentsByStatus(any(Instant.class), eq(AgentStatus.ACTIVE)))
          .thenReturn(List.of());

      // Act
      job.execute(null);
      job.execute(null);

      // Assert
      verify(agentRepository, times(2))
          .findStaleAgentsByStatus(any(Instant.class), eq(AgentStatus.ACTIVE));
      verify(agentRepository, never()).saveAll(any());
      verifyNoInteractions(auditLogger);
    }
  }
}
