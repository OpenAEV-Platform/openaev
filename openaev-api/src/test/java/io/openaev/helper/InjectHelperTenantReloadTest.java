package io.openaev.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.InjectStatus;
import io.openaev.execution.ExecutableInject;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The scheduler polls the injects to run across all tenants and runs each one in its own tenant.
 * Not {@code @Transactional}: the poll and the reload open their own transactions, as in
 * production, so the rows are committed in their tenants and removed with them.
 */
@WithMockUser(isAdmin = true)
@TestInstance(PER_CLASS)
@DisplayName("Inject to run reloaded in its own tenant")
class InjectHelperTenantReloadTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private record Seeded(String injectId, String endpointId, String agentId) {}

  @Autowired private InjectHelper injectHelper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EntityManager entityManager;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;

  private String injectTenantId;
  private String otherTenantId;

  @BeforeEach
  void createTheTenantsAndResetComposers() throws Exception {
    // Created here, not in @BeforeAll: tenant creation enrols the mock user, created per test
    if (injectTenantId == null) {
      injectTenantId = tenantHelper.createTenant("inject-reload-own").getId();
      otherTenantId = tenantHelper.createTenant("inject-reload-other").getId();
    }
    injectComposer.reset();
    injectStatusComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
  }

  @AfterAll
  void deleteTheTenants() {
    tenantHelper.deleteCommittedTenants(injectTenantId, otherTenantId);
  }

  @Nested
  @DisplayName("Agents of a directly targeted endpoint")
  class Agents {

    @Test
    @DisplayName(
        "given an endpoint that also carries an agent of another tenant should reload the inject"
            + " with its own tenant's agent only")
    void given_endpointWithAgentOfAnotherTenant_should_reloadWithOwnAgentOnly() {
      // Arrange
      Seeded seeded = seedAtomicTesting(ExecutionStatus.QUEUING);
      String foreignAgentId = inTenant(otherTenantId, () -> addInactiveAgent(seeded.endpointId()));

      // Act
      List<InjectToRun> polled = injectHelper.getInjectsToRun();
      List<String> agentIds =
          inTenant(
              injectTenantId,
              () -> agentIdsOf(injectHelper.executableInject(seeded.injectId()).orElseThrow()));

      // Assert
      assertThat(polled)
          .filteredOn(injectToRun -> injectToRun.injectId().equals(seeded.injectId()))
          .singleElement()
          .extracting(InjectToRun::tenantId)
          .isEqualTo(injectTenantId);
      assertThat(agentIds).containsExactly(seeded.agentId()).doesNotContain(foreignAgentId);
    }
  }

  @Nested
  @DisplayName("Status at reload")
  class Status {

    @Test
    @DisplayName("given an inject started since the poll should not reload it")
    void given_injectStartedSincePoll_should_notReloadIt() {
      // Arrange
      Seeded seeded = seedAtomicTesting(ExecutionStatus.EXECUTING);

      // Act
      Optional<ExecutableInject> reloaded =
          inTenant(injectTenantId, () -> injectHelper.executableInject(seeded.injectId()));

      // Assert
      assertThat(reloaded).isEmpty();
    }
  }

  private Seeded seedAtomicTesting(ExecutionStatus statusName) {
    return inTenant(
        injectTenantId,
        () -> {
          InjectStatus status = new InjectStatus();
          status.setName(statusName);
          AgentComposer.Composer agent =
              agentComposer.forAgent(AgentFixture.createDefaultAgentService());
          EndpointComposer.Composer endpoint =
              endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).withAgent(agent);
          InjectComposer.Composer inject =
              injectComposer
                  .forInject(InjectFixture.getDefaultInject())
                  .withInjectStatus(injectStatusComposer.forInjectStatus(status))
                  .withEndpoint(endpoint)
                  .persist();
          return new Seeded(inject.get().getId(), endpoint.get().getId(), agent.get().getId());
        });
  }

  private String addInactiveAgent(String endpointId) {
    Agent agent = AgentFixture.createInactiveAgent();
    agent.setAsset(entityManager.getReference(Asset.class, endpointId));
    // persist, not save: a merge would read the endpoint, which this tenant cannot see
    entityManager.persist(agent);
    return agent.getId();
  }

  private static List<String> agentIdsOf(ExecutableInject executableInject) {
    return executableInject.getAssets().stream()
        .map(asset -> (Endpoint) Hibernate.unproxy(asset))
        .flatMap(endpoint -> endpoint.getAgents().stream())
        .map(Agent::getId)
        .toList();
  }

  private <T> T inTenant(String tenantId, Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
