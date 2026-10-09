package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.WithoutTenantScope;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code datapacks} activated, the tenant scope isolates the table
 * through the real {@link DataPackService} and its composite key: the same {@code datapack_id} can
 * exist once per tenant, and a read under one tenant's scope never reaches another tenant's row
 * even when the caller supplies that other tenant's id explicitly.
 *
 * <p>{@code datapacks} has no controller: it is a provisioning-only table written by {@code
 * MigrationProcessor} at startup and onboarding. The test therefore drives {@link DataPackService}
 * directly and sets the scope with {@link TenantScopedTransaction#setScopeOnCurrentTransaction},
 * the primitive's join-an-ambient-transaction entry point, since the class runs inside one
 * {@code @Transactional} test transaction (rolled back after each test) rather than opening its own
 * through {@code execute()}. Rows are seeded with a native {@code INSERT ... VALUES}, which the
 * inspector never blocks, so seeding itself proves nothing about isolation; only the reads below
 * do.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=datapacks")
@WithMockUser(isAdmin = true)
@WithoutTenantScope
class DataPackTenantIsolationTest extends IntegrationTest {

  @Autowired private DataPackService dataPackService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String sharedPackId;

  @BeforeEach
  void seedTwoTenantsWithTheSameDatapackId() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("datapack-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("datapack-iso-b").getId();
    sharedPackId = "iso-test-pack-" + UUID.randomUUID();
    seedDatapack(sharedPackId, tenantA);
    seedDatapack(sharedPackId, tenantB);
  }

  @Test
  @DisplayName("under tenant A's scope: A's row of the shared id is found, B's is not")
  void given_sameDatapackIdInTwoTenants_should_beIsolatedPerTenant() {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
    assertTrue(
        dataPackService.findByIdAndTenant(sharedPackId, new Tenant(tenantA)).isPresent(),
        "A's own row of the shared datapack id must be found under A's scope");
    assertTrue(
        dataPackService.findByIdAndTenant(sharedPackId, new Tenant(tenantB)).isEmpty(),
        "B's row of the shared datapack id must not be found under A's scope, even though the"
            + " caller supplied B's tenant explicitly in the composite key");
  }

  @Test
  @DisplayName("with no scope set at all: even the caller's own tenant fails closed")
  void given_noScopeSet_should_failClosedEvenForOwnTenant() {
    // Second break, on a different line than the isolation assertion above: proves the read
    // above succeeds BECAUSE the scope is set, not because the composite-key predicate alone is
    // enough once the table is active.
    assertTrue(
        dataPackService.findByIdAndTenant(sharedPackId, new Tenant(tenantA)).isEmpty(),
        "an active table read with no TxCtx scope on the transaction must fail closed, never fall"
            + " back to the composite key predicate alone");
  }

  @Test
  @DisplayName("registerDataPack attributes the new row to the given tenant")
  void given_registerDataPack_should_attributeRowToGivenTenant() {
    String newId = "iso-test-pack-new-" + UUID.randomUUID();
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
    dataPackService.registerDataPack(newId, new Tenant(tenantA));
    assertEquals(tenantA, rawTenantOf(newId));
  }

  // -- helpers --

  private void seedDatapack(String datapackId, String tenantId) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "INSERT INTO datapacks (datapack_id, tenant_id) VALUES (?, ?)")) {
                statement.setString(1, datapackId);
                statement.setString(2, tenantId);
                statement.executeUpdate();
              }
            });
  }

  private String rawTenantOf(String datapackId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM datapacks WHERE datapack_id = ?")) {
                statement.setString(1, datapackId);
                try (ResultSet resultSet = statement.executeQuery()) {
                  return resultSet.next() ? resultSet.getString(1) : null;
                }
              }
            });
  }
}
