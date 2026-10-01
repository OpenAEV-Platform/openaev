package io.openaev.rest.injector_contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.injectors.email.EmailContract;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Onboarding a tenant copies the platform's default injector contracts into it ({@code
 * InjectorContractService#createDependencyForTenant}), reading source rows that live in the default
 * tenant. With {@code domains} v2-active, the source contract's eagerly fetched {@code domains}
 * collection is a gated read, so the copy step cannot be what decides which domains the new
 * tenant's contracts end up with.
 *
 * <p>What makes the end state correct is the built-in injector registration that runs later in the
 * same onboarding transaction, scoped to the new tenant, and re-resolves every contract's domains
 * inside it. These tests pin that end state under both scopes the copy step can see: none (a
 * fail-closed read) and the default tenant (what an operator's request scope holds in production,
 * where the source rows exist). Both halves of the assertion matter, since "no domain at all" and
 * "the default tenant's domain" are the two ways this can go wrong, and a filtered read and an
 * empty table look identical without the non-empty one.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=domains")
@WithMockUser(isAdmin = true)
@DisplayName("tenant onboarding links the provisioned contracts to the new tenant's own domains")
class InjectorContractTenantProvisioningDomainTest extends IntegrationTest {

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  @Nested
  @DisplayName("the copy step reads the default tenant's contracts with no scope")
  class WithNoScopeOnTheCopyStep {

    @Test
    @DisplayName("the provisioned contracts still carry at least one domain")
    void given_an_unscoped_copy_step_should_still_link_the_contracts_to_a_domain()
        throws Exception {
      // Arrange
      seedDefaultTenantEmailContractWithItsOwnDomain();

      // Act
      String tenantId = tenantHelper.createTenantWithCurrentUser("ic-prov-unscoped").getId();

      // Assert
      assertThat(countContractDomainLinks(tenantId))
          .as(
              "a provisioned contract with no domain link is indistinguishable from a filtered"
                  + " read, so the non-empty count is the assertion with teeth")
          .isPositive();
      assertThat(countForeignContractDomainLinks(tenantId)).isZero();
    }
  }

  @Nested
  @DisplayName("the copy step reads them under the default tenant, as an operator request does")
  class WithTheDefaultTenantInScopeOnTheCopyStep {

    @Test
    @DisplayName("the provisioned contracts carry the new tenant's domains, not the source's")
    void given_a_copy_step_scoped_to_the_default_tenant_should_not_link_a_foreign_domain()
        throws Exception {
      // Arrange
      String sourceDomainId = seedDefaultTenantEmailContractWithItsOwnDomain();
      setScope(Tenant.DEFAULT_TENANT_UUID);

      // Act
      String tenantId = tenantHelper.createTenantWithCurrentUser("ic-prov-scoped").getId();

      // Assert
      assertThat(countContractDomainLinks(tenantId)).isPositive();
      assertThat(countForeignContractDomainLinks(tenantId))
          .as(
              "the source contract's domain belongs to the default tenant; reaching the new"
                  + " tenant's join rows would make it a cross-tenant reference")
          .isZero();
      assertThat(countLinksToDomain(tenantId, sourceDomainId)).isZero();
    }
  }

  /**
   * One default-tenant source contract carrying one default-tenant domain, which is what the copy
   * step finds on a real platform and what the test database does not hold on its own.
   *
   * @return the id of the default tenant's domain
   */
  private String seedDefaultTenantEmailContractWithItsOwnDomain() {
    String domainId = UUID.randomUUID().toString();
    rawUpdate(
        "INSERT INTO domains (domain_id, domain_name, domain_color, tenant_id)"
            + " VALUES (?, ?, '#101010', ?)",
        domainId,
        "Provisioning source domain " + domainId,
        Tenant.DEFAULT_TENANT_UUID);
    rawUpdate(
        "INSERT INTO injectors_contracts (injector_contract_id, tenant_id,"
            + " injector_contract_content) VALUES (?, ?, '{}')",
        EmailContract.EMAIL_DEFAULT,
        Tenant.DEFAULT_TENANT_UUID);
    rawUpdate(
        "INSERT INTO injectors_contracts_domains (injector_contract_id, tenant_id, domain_id)"
            + " VALUES (?, ?, ?)",
        EmailContract.EMAIL_DEFAULT,
        Tenant.DEFAULT_TENANT_UUID,
        domainId);
    return domainId;
  }

  /** Sets the transaction-local tenant scope, the way the transaction aspect does for a request. */
  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  /** Join rows of this tenant's contracts whose domain row exists, whatever tenant owns it. */
  private long countContractDomainLinks(String tenantId) {
    return rawCount(
        "SELECT count(*) FROM injectors_contracts_domains link"
            + " JOIN domains d ON d.domain_id = link.domain_id"
            + " WHERE link.tenant_id = ?",
        tenantId);
  }

  /** The same join rows, restricted to those pointing at another tenant's domain. */
  private long countForeignContractDomainLinks(String tenantId) {
    return rawCount(
        "SELECT count(*) FROM injectors_contracts_domains link"
            + " JOIN domains d ON d.domain_id = link.domain_id"
            + " WHERE link.tenant_id = ? AND d.tenant_id <> ?",
        tenantId,
        tenantId);
  }

  private long countLinksToDomain(String tenantId, String domainId) {
    return rawCount(
        "SELECT count(*) FROM injectors_contracts_domains"
            + " WHERE tenant_id = ? AND domain_id = ?",
        tenantId,
        domainId);
  }

  // Ground truth, bypassing the scope: raw JDBC on the test's own connection sees the uncommitted
  // onboarding rows, and the rewriter does not touch a statement it never generated.
  private long rawCount(String sql, String... parameters) {
    return rawWork(
        sql,
        parameters,
        statement -> {
          try (ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
          }
        });
  }

  private void rawUpdate(String sql, String... parameters) {
    rawWork(sql, parameters, statement -> (long) statement.executeUpdate());
  }

  private long rawWork(String sql, String[] parameters, StatementRunner runner) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                  statement.setString(i + 1, parameters[i]);
                }
                return runner.run(statement);
              }
            });
  }

  private interface StatementRunner {
    long run(PreparedStatement statement) throws SQLException;
  }
}
