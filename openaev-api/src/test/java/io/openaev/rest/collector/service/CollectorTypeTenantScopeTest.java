package io.openaev.rest.collector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CollectorType;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read isolation for {@code collector_types}, the half {@link
 * io.openaev.rest.collector.CollectorTypeHttpIsolationTest} does not cover: that class proves the
 * write attribution of the two HTTP routes, and every other assertion on this table in the test
 * tree is produced by an explicit {@code ...AndTenantId} predicate, so none of them changes when
 * the table is de-activated.
 *
 * <p>The read under test is {@link CollectorTypeRepository#findByName}, which carries no tenant
 * predicate at all. Its production caller is {@code PayloadUpsertService#upsertPayload}, the
 * collector payload ingestion path: it resolves the collector's type by name only, so with {@code
 * collector_types} active the tenant scope is the single thing that decides which row it gets. The
 * repository's own javadoc names the hazard this pins: {@code collector_type_name} is unique per
 * tenant, not globally, so an unscoped lookup by name can reach another tenant's row.
 *
 * <p>Rows are seeded with a native {@code INSERT ... VALUES}, which the inspector never rewrites,
 * so the seeding itself proves nothing; only the reads do.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=collector_types")
@WithMockUser(isAdmin = true)
@DisplayName("collector_types read isolation under a tenant scope")
class CollectorTypeTenantScopeTest extends IntegrationTest {

  @Autowired private CollectorTypeRepository collectorTypeRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String typeNameA;
  private String typeNameB;
  private String sharedTypeName;
  private String typeIdB;

  @BeforeEach
  void seedTwoTenantsWithTheirOwnCollectorTypes() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("collector-type-scope-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("collector-type-scope-b").getId();
    TenantContext.clearCurrentTenant();

    typeNameA = "openaev_scope_a_" + UUID.randomUUID();
    typeNameB = "openaev_scope_b_" + UUID.randomUUID();
    sharedTypeName = "openaev_scope_shared_" + UUID.randomUUID();

    seedCollectorType(typeNameA, tenantA);
    typeIdB = seedCollectorType(typeNameB, tenantB);
    seedCollectorType(sharedTypeName, tenantA);
    seedCollectorType(sharedTypeName, tenantB);
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Lookup by name, the collector payload ingestion read")
  class ByName {

    @Test
    @DisplayName("under tenant A's scope: A's type is found by name and B's is not")
    void given_tenantAScope_should_notFindTenantBCollectorTypeByName() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act
      Optional<CollectorType> own = collectorTypeRepository.findByName(typeNameA);
      Optional<CollectorType> other = collectorTypeRepository.findByName(typeNameB);

      // Assert
      assertTrue(own.isPresent(), "A's own collector type must be found by name under A's scope");
      assertEquals(tenantA, own.get().getTenant().getId());
      assertTrue(
          other.isEmpty(),
          "tenant B's collector type must not be reachable by name under tenant A's scope: the"
              + " lookup carries no tenant predicate, so the scope is the only thing hiding it");
    }

    @Test
    @DisplayName("the same type name in both tenants resolves to exactly one row, the scope's")
    void given_sameTypeNameInTwoTenants_should_resolveToTheScopedRowOnly() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantB));

      // Act
      Optional<CollectorType> resolved = collectorTypeRepository.findByName(sharedTypeName);

      // Assert - without the scope this single-result lookup sees two rows and cannot answer at
      // all,
      // which is the per-tenant uniqueness hazard the repository javadoc describes.
      assertTrue(resolved.isPresent(), "B's row of the shared type name must be found");
      assertEquals(
          tenantB,
          resolved.get().getTenant().getId(),
          "a name shared by two tenants must resolve to the scoped tenant's row");
    }

    @Test
    @DisplayName("with no scope at all: even the caller's own type fails closed")
    void given_noScopeSet_should_failClosedEvenForItsOwnTenant() {
      // Act & Assert - the control for the test above, on a different line than the active-tables
      // property: the lookup succeeds BECAUSE a scope is set, not because the name alone is enough
      // once the table is active.
      assertTrue(
          collectorTypeRepository.findByName(typeNameA).isEmpty(),
          "an active-table lookup with no tenant scope on the transaction must fail closed, never"
              + " fall back to matching the name across tenants");
    }
  }

  @Nested
  @DisplayName("Lookup by id")
  class ById {

    @Test
    @DisplayName("under tenant A's scope: tenant B's collector type is not readable by id")
    void given_tenantAScope_should_notReadTenantBCollectorTypeById() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantB));
      assertTrue(
          collectorTypeRepository.findById(typeIdB).isPresent(),
          "B's own collector type must be readable by id under B's scope");

      // Act & Assert
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
      entityManager.clear();
      assertTrue(
          collectorTypeRepository.findById(typeIdB).isEmpty(),
          "tenant B's collector type must not be readable by id under tenant A's scope");
    }
  }

  // -- seeding --

  private String seedCollectorType(String name, String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "INSERT INTO collector_types (collector_type_id, collector_type_name,"
                          + " tenant_id) VALUES (?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, name);
                statement.setString(3, tenantId);
                statement.executeUpdate();
              }
            });
    return id;
  }
}
