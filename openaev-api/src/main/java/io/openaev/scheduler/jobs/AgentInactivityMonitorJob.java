package io.openaev.scheduler.jobs;

import static io.openaev.aop.audit_log.AuditEventOrigin.SYSTEM;
import static io.openaev.database.model.Agent.ACTIVE_THRESHOLD_MILLIS;

import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AgentStatus;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.EventType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.AgentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class AgentInactivityMonitorJob implements Job {

  public static final String AGENT_INACTIVITY_MONITOR_JOB = "AgentInactivityMonitorJob";
  public static final String AGENT_INACTIVITY_MONITOR_TRIGGER = "AgentInactivityMonitorTrigger";

  private final AgentRepository agentRepository;
  private final TenantScopedTransaction tenantTx;
  private final Optional<AuditLogger> auditLogger;

  @Override
  public void execute(JobExecutionContext context) {
    tenantTx.forEachTenant(this::markInactiveAgentsForTenant);
  }

  private void markInactiveAgentsForTenant(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      Instant threshold = Instant.now().minus(ACTIVE_THRESHOLD_MILLIS, ChronoUnit.MILLIS);
      List<Agent> newlyInactiveAgents =
          agentRepository.findStaleAgentsByStatus(threshold, AgentStatus.ACTIVE);
      if (newlyInactiveAgents.isEmpty()) {
        return;
      }

      newlyInactiveAgents.forEach(agent -> agent.setStatus(AgentStatus.INACTIVE));
      agentRepository.saveAll(newlyInactiveAgents);
      newlyInactiveAgents.forEach(this::logCoverageGap);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private void logCoverageGap(Agent agent) {
    auditLogger.ifPresent(
        logger -> {
          LinkedHashMap<String, Object> contextData = new LinkedHashMap<>();
          contextData.put("agent_id", agent.getId());
          contextData.put(
              "endpoint_id", agent.getAsset() != null ? agent.getAsset().getId() : "unknown");
          contextData.put(
              "last_seen", agent.getLastSeen() != null ? agent.getLastSeen().toString() : "never");
          contextData.put("previous_status", AgentStatus.ACTIVE.name());
          contextData.put("new_status", AgentStatus.INACTIVE.name());

          logger.logEvent(
              AuditEvent.builder()
                  .eventType(EventType.EXECUTION)
                  .eventScope(AuditEventScope.COVERAGE_GAP)
                  .eventStatus(EventStatus.WARNING)
                  .resourceType(ResourceType.AGENT)
                  .resourceId(agent.getId())
                  .message(
                      "Agent '%s' became inactive (no heartbeat for 1 hour)"
                          .formatted(agent.getId()))
                  .contextData(contextData)
                  .origin(SYSTEM)
                  .build());
        });
  }
}
