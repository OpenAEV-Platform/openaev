package io.openaev.service.catalog.scenario.update;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.scenario.ScenarioRevision;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.ScenarioRevisionRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@CoversTenantIsolation("scenario_revisions")
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("scenario_revisions read isolation under a tenant scope")
class ScenarioRevisionIsolationTest extends IntegrationTest {

  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ScenarioRevisionRepository scenarioRevisionRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String revisionIdA;

  @BeforeEach
  void seedTwoTenantsWithOneRevisionEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("scenario-revision-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("scenario-revision-b").getId();
    revisionIdA = scenarioRevisionRepository.save(revision(tenantA)).getId();
    scenarioRevisionRepository.save(revision(tenantB));
    TenantContext.clearCurrentTenant();
    // Out of the persistence context, so the read issues the SELECT the scope rewrites.
    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("under tenant A's scope, only A's revision is read")
  void given_tenantAScope_should_notReadTenantBRevision() {
    // Arrange
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

    // Act & Assert
    assertThat(scenarioRevisionRepository.findAll())
        .extracting(ScenarioRevision::getId)
        .containsExactly(revisionIdA);
  }

  private ScenarioRevision revision(String tenantId) {
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(tenantId));
    ScenarioRevision revision = new ScenarioRevision();
    revision.setTenant(new Tenant(tenantId));
    revision.setScenario(scenarioRepository.save(scenario));
    revision.setReason(ScenarioRevision.Reason.UPDATE);
    revision.setCatalogVersion("1.0.0");
    revision.setContent(JsonNodeFactory.instance.objectNode());
    return revision;
  }
}
