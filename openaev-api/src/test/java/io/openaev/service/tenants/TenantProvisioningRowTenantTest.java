package io.openaev.service.tenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating a tenant writes the injector contracts, their domain links and the built-in injector
 * link of the new tenant while the transaction's scope is the creator's. The write attribution
 * baseline lists those three tables for that call, so this class pins what the baseline cannot:
 * every row the creation adds belongs to the new tenant, no other tenant gains or loses a row, and
 * each link row joins a contract, a domain and an injector of its own tenant.
 *
 * <p>The counts are read with raw JDBC on the test's own connection, which sees the uncommitted
 * onboarding rows and is not rewritten by the statement inspector, so the result does not depend on
 * the scope in force.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("tenant creation writes the contract provisioning rows in the new tenant only")
class TenantProvisioningRowTenantTest extends ProductionTablesIntegrationTest {

  private static final List<String> PROVISIONED_TABLES =
      List.of("injectors_contracts", "injectors_contracts_domains", "injectors_injector_contracts");

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  @Nested
  @DisplayName("whatever scope the creator's transaction holds")
  class WhateverTheCreatorScope {

    @Test
    @DisplayName("with no scope the new tenant alone gains rows")
    void given_no_scope_should_add_the_provisioning_rows_to_the_new_tenant_only() throws Exception {
      // Arrange
      Map<String, Map<String, Long>> before = countsByTenant();

      // Act
      String tenantId = tenantHelper.createTenantWithCurrentUser("provisioning-unscoped").getId();

      // Assert
      assertOnlyTheNewTenantChanged(before, tenantId);
      assertLinksJoinRowsOfTheirOwnTenant();
    }

    @Test
    @DisplayName("with the default tenant in scope the new tenant alone gains rows")
    void given_the_default_tenant_scope_should_add_the_provisioning_rows_to_the_new_tenant_only()
        throws Exception {
      // Arrange
      setScope(Tenant.DEFAULT_TENANT_UUID);
      Map<String, Map<String, Long>> before = countsByTenant();

      // Act
      String tenantId = tenantHelper.createTenantWithCurrentUser("provisioning-scoped").getId();

      // Assert
      assertOnlyTheNewTenantChanged(before, tenantId);
      assertLinksJoinRowsOfTheirOwnTenant();
    }
  }

  private void assertOnlyTheNewTenantChanged(
      Map<String, Map<String, Long>> before, String newTenantId) {
    Map<String, Map<String, Long>> after = countsByTenant();
    for (String table : PROVISIONED_TABLES) {
      Map<String, Long> tenantsBefore = before.get(table);
      Map<String, Long> tenantsAfter = after.get(table);
      tenantsAfter.forEach(
          (tenantId, count) -> {
            if (!tenantId.equals(newTenantId)) {
              assertThat(count)
                  .as("%s rows of tenant %s must be unchanged by the creation", table, tenantId)
                  .isEqualTo(tenantsBefore.getOrDefault(tenantId, 0L));
            }
          });
      tenantsBefore.forEach(
          (tenantId, count) ->
              assertThat(tenantsAfter)
                  .as("%s rows of tenant %s must not disappear", table, tenantId)
                  .containsKey(tenantId));
      assertThat(tenantsAfter.getOrDefault(newTenantId, 0L))
          .as("the creation must provision %s rows for the new tenant", table)
          .isPositive();
    }
  }

  private void assertLinksJoinRowsOfTheirOwnTenant() {
    assertThat(
            scalar(
                "SELECT count(*) FROM injectors_contracts_domains link"
                    + " JOIN domains d ON d.domain_id = link.domain_id"
                    + " WHERE d.tenant_id <> link.tenant_id"))
        .as("a contract-domain link must point at a domain of its own tenant")
        .isZero();
    assertThat(
            scalar(
                "SELECT count(*) FROM injectors_injector_contracts link"
                    + " JOIN injectors i ON i.injector_id = link.injector_id"
                    + " WHERE i.tenant_id <> link.tenant_id"))
        .as("an injector-contract link must point at an injector of its own tenant")
        .isZero();
  }

  private Map<String, Map<String, Long>> countsByTenant() {
    Map<String, Map<String, Long>> counts = new HashMap<>();
    for (String table : PROVISIONED_TABLES) {
      counts.put(table, countsOf(table));
    }
    return counts;
  }

  private Map<String, Long> countsOf(String table) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              Map<String, Long> byTenant = new HashMap<>();
              try (PreparedStatement statement =
                      connection.prepareStatement(
                          "SELECT tenant_id, count(*) FROM " + table + " GROUP BY tenant_id");
                  ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                  byTenant.put(rows.getString(1), rows.getLong(2));
                }
              }
              return byTenant;
            });
  }

  private long scalar(String sql) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql);
                  ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
              }
            });
  }

  /** Sets the transaction-local tenant scope, the way the transaction aspect does for a request. */
  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }
}
