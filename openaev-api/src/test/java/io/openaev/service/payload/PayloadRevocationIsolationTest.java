package io.openaev.service.payload;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.PayloadRevocationRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@CoversTenantIsolation("payload_revocations")
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("payload_revocations read isolation under a tenant scope")
class PayloadRevocationIsolationTest extends IntegrationTest {

  @Autowired private PayloadRepository payloadRepository;
  @Autowired private PayloadRevocationRepository payloadRevocationRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String revocationIdA;

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @BeforeEach
  void seedTwoTenantsWithOneRevocationEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("payload-revocation-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("payload-revocation-b").getId();
    revocationIdA = payloadRevocationRepository.save(revocation(tenantA)).getId();
    payloadRevocationRepository.save(revocation(tenantB));
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
  @DisplayName("under tenant A's scope, only A's revocation is read")
  void given_tenantAScope_should_notReadTenantBRevocation() {
    // Arrange
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

    // Act & Assert
    assertThat(payloadRevocationRepository.findAll())
        .extracting(PayloadRevocation::getId)
        .containsExactly(revocationIdA);
  }

  private PayloadRevocation revocation(String tenantId) {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setTenant(new Tenant(tenantId));
    PayloadRevocation revocation = new PayloadRevocation();
    revocation.setTenant(new Tenant(tenantId));
    revocation.setPayload(payloadRepository.save(payload));
    revocation.setReason("Deletes user files");
    return revocation;
  }
}
