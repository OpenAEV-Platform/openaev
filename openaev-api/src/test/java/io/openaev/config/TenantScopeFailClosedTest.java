package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The safety net behind the {@code TxCtx} parameter. If a {@code @Transactional} method on an
 * active tenant table ever loses its {@code TxCtx} (a refactor, a new endpoint, a background job),
 * the transaction aspect sets no scope. This proves the table then goes dark rather than leaking:
 * with {@code import_mappers} active and no scope set, a read returns nothing even though the row
 * exists. Fail-closed, not fail-open. This is why dropping the parameter is a robustness bug, not a
 * data leak.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@WithMockUser(isAdmin = true)
@DisplayName("A read with no tenant scope sees nothing (fail-closed)")
class TenantScopeFailClosedTest extends IntegrationTest {

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ImportMapperRepository importMapperRepository;

  @Test
  @DisplayName("no scope set: the read is empty although the row exists")
  void readWithoutScopeReturnsNothing() throws Exception {
    Tenant tenant = tenantHelper.createTenantWithCurrentUser("fail-closed");
    String id = seedMapper(tenant.getId(), "hidden");

    // No TxCtx in this transaction, so the aspect never set app.current_tenants and the inspector's
    // can_access_tenant denies every row.
    assertEquals(0L, importMapperRepository.count(), "a scope-less read must see no rows");
    assertTrue(
        importMapperRepository.findById(UUID.fromString(id)).isEmpty(),
        "a scope-less lookup must not find the row");

    // Ground truth via raw JDBC (bypasses the inspector): the row really exists, it is only hidden.
    assertEquals(1L, rawCount(id), "the row exists, it is only hidden by the missing scope");
  }

  private String seedMapper(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO import_mappers (mapper_id, mapper_name, mapper_inject_type_column, tenant_id)"
                + " VALUES (CAST(:id AS uuid), :name, :col, :tenant)")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("col", "inject_type")
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private long rawCount(String mapperId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM import_mappers WHERE mapper_id = CAST(? AS uuid)")) {
                statement.setString(1, mapperId);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
