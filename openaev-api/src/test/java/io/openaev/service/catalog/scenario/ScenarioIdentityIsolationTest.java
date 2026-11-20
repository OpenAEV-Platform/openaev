package io.openaev.service.catalog.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.scenario.ScenarioIdentity;
import io.openaev.database.repository.ScenarioIdentityRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@CoversTenantIsolation("scenario_identities")
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("scenario_identities read isolation under a tenant scope")
class ScenarioIdentityIsolationTest extends IntegrationTest {

  @Autowired private ScenarioIdentityRepository scenarioIdentityRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String identityIdA;

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @BeforeEach
  void seedTwoTenantsWithOneIdentityEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("scenario-identity-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("scenario-identity-b").getId();
    identityIdA = scenarioIdentityRepository.save(identity(tenantA)).getId();
    scenarioIdentityRepository.save(identity(tenantB));
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
  @DisplayName("under tenant A's scope, only A's scenario identity is read")
  void given_tenantAScope_should_notReadTenantBIdentity() {
    // Arrange
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

    // Act & Assert
    assertThat(scenarioIdentityRepository.findAll())
        .extracting(ScenarioIdentity::getId)
        .containsExactly(identityIdA);
  }

  private static ScenarioIdentity identity(String tenantId) {
    ScenarioIdentity identity = new ScenarioIdentity();
    identity.setTenant(new Tenant(tenantId));
    identity.setRepository("filigran/openaev-scenarios");
    identity.setExternalId("scenario-" + tenantId);
    return identity;
  }
}
