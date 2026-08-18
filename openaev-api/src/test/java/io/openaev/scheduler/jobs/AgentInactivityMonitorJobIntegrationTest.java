package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AgentStatus;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class AgentInactivityMonitorJobIntegrationTest extends IntegrationTest {

  @Autowired private AgentInactivityMonitorJob agentInactivityMonitorJob;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private void inTransaction(Runnable work) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
  }

  @Test
  @DisplayName("given agent lifecycle should transition inactive active inactive")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void given_agentLifecycle_should_transitionInactiveActiveInactive() {
    String[] ids = new String[2];

    try {
      // Arrange
      inTransaction(
          () -> {
            Endpoint endpoint = EndpointFixture.createEndpoint("lifecycle-endpoint");
            endpoint.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
            endpointRepository.save(endpoint);

            Agent agent = AgentFixture.createDefaultAgentService();
            agent.setAsset(endpoint);
            agent.setTenant(endpoint.getTenant());
            agent.setStatus(AgentStatus.ACTIVE);
            agent.setLastSeen(Instant.now().minus(2, ChronoUnit.HOURS));
            agentRepository.save(agent);

            ids[0] = endpoint.getId();
            ids[1] = agent.getId();
          });

      // Act 1: stale active agent becomes inactive
      agentInactivityMonitorJob.execute(null);

      // Assert 1
      inTransaction(
          () -> {
            Agent persisted = agentRepository.findById(ids[1]).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(AgentStatus.INACTIVE);
          });

      // Arrange 2: simulate heartbeat recovery (reactivation)
      inTransaction(
          () -> {
            Agent recovered = agentRepository.findById(ids[1]).orElseThrow();
            recovered.setStatus(AgentStatus.ACTIVE);
            recovered.setLastSeen(Instant.now());
            agentRepository.save(recovered);
          });

      // Act 2: recent active agent must stay active
      agentInactivityMonitorJob.execute(null);

      // Assert 2
      inTransaction(
          () -> {
            Agent persisted = agentRepository.findById(ids[1]).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(AgentStatus.ACTIVE);
          });

      // Arrange 3: stale again
      inTransaction(
          () -> {
            Agent staleAgain = agentRepository.findById(ids[1]).orElseThrow();
            staleAgain.setLastSeen(Instant.now().minus(2, ChronoUnit.HOURS));
            agentRepository.save(staleAgain);
          });

      // Act 3: stale active agent becomes inactive again
      agentInactivityMonitorJob.execute(null);

      // Assert 3
      inTransaction(
          () -> {
            Agent persisted = agentRepository.findById(ids[1]).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(AgentStatus.INACTIVE);
          });

    } finally {
      inTransaction(
          () -> {
            if (ids[1] != null) {
              agentRepository.deleteById(ids[1]);
            }
            if (ids[0] != null) {
              endpointRepository.deleteById(ids[0]);
            }
          });
    }
  }
}
