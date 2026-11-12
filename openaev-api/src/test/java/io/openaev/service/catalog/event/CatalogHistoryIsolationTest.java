package io.openaev.service.catalog.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.database.repository.CatalogHistoryRepository;
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

@CoversTenantIsolation("catalog_history")
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("catalog_history read isolation under a tenant scope")
class CatalogHistoryIsolationTest extends IntegrationTest {

  @Autowired private CatalogHistoryRepository catalogHistoryRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String historyIdA;

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @BeforeEach
  void seedTwoTenantsWithOneHistoryRowEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("catalog-history-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("catalog-history-b").getId();
    historyIdA = catalogHistoryRepository.save(history(tenantA)).getId();
    catalogHistoryRepository.save(history(tenantB));
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
  @DisplayName("under tenant A's scope, only A's history row is read")
  void given_tenantAScope_should_notReadTenantBHistory() {
    // Arrange
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

    // Act & Assert
    assertThat(catalogHistoryRepository.findAll())
        .extracting(CatalogHistory::getId)
        .containsExactly(historyIdA);
  }

  private static CatalogHistory history(String tenantId) {
    CatalogHistory history = new CatalogHistory();
    history.setTenant(new Tenant(tenantId));
    history.setType(ContentType.PAYLOAD);
    history.setRepository("filigran/openaev-payloads");
    history.setExternalId("payload-" + tenantId);
    history.setVersion("1.0.0");
    history.setAction(CatalogHistory.Action.INSTALL);
    history.setMode(CatalogHistory.Mode.MANUAL);
    return history;
  }
}
