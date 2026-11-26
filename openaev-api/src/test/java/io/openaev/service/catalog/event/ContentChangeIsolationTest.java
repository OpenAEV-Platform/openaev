package io.openaev.service.catalog.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.catalog.ContentChange;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.database.repository.ContentChangeRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@CoversTenantIsolation("content_changes")
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("content_changes read isolation under a tenant scope")
class ContentChangeIsolationTest extends ProductionTablesIntegrationTest {

  @Autowired private ContentChangeRepository contentChangeRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String historyIdA;

  @BeforeEach
  void seedTwoTenantsWithOneHistoryRowEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("catalog-history-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("catalog-history-b").getId();
    historyIdA = contentChangeRepository.save(history(tenantA)).getId();
    contentChangeRepository.save(history(tenantB));
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
    assertThat(contentChangeRepository.findAll())
        .extracting(ContentChange::getId)
        .containsExactly(historyIdA);
  }

  private static ContentChange history(String tenantId) {
    ContentChange history = new ContentChange();
    history.setTenant(new Tenant(tenantId));
    history.setType(ContentType.PAYLOAD);
    history.setRepository("filigran/openaev-payloads");
    history.setExternalId("payload-" + tenantId);
    history.setVersion("1.0.0");
    history.setAction(ContentChange.Action.INSTALL);
    history.setMode(ContentChange.Mode.MANUAL);
    return history;
  }
}
