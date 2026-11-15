package io.openaev.utils;

import static io.openaev.utils.TagLinkJdbc.saveTag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TagRepository;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Cleanup of committed test tenants by the tenant isolation helper")
class TenantIsolationTestHelperTest extends IntegrationTest {

  private static final String AMBIENT_SCOPE_QUERY =
      "SELECT coalesce(current_setting('app.current_tenants', true), '') || '|' || "
          + "coalesce(current_setting('"
          + DefaultTenantScopeTestListener.AMBIENT_SCOPE_SETTING
          + "', true), '')";

  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;
  @Autowired private TagRepository tagRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private DataSource dataSource;

  private final List<String> committedTenantIds = new ArrayList<>();

  @AfterEach
  void cleanupCommittedTenants() {
    if (committedTenantIds.isEmpty()) {
      return;
    }
    tenantIsolationTestHelper.deleteCommittedTenants(committedTenantIds.toArray(String[]::new));
    committedTenantIds.clear();
  }

  @Nested
  @DisplayName("Called inside the test-managed transaction")
  class InsideTestManagedTransaction {

    @Test
    @DisplayName(
        "Given a committed tenant with an uncommitted child row, should delete the tenant, start a"
            + " new test transaction and restore the remaining ambient scope")
    void given_committedTenantWithUncommittedChild_should_deleteTenantAndRestoreRemainingScope()
        throws Exception {
      // Arrange
      List<String> tenantIds = commitTenants("cleanup-removed", "cleanup-kept");
      String removedTenantId = tenantIds.get(0);
      String keptTenantId = tenantIds.get(1);
      DefaultTenantScopeTestListener.setAmbientScope(
          entityManager,
          String.join(",", Tenant.DEFAULT_TENANT_UUID, removedTenantId, keptTenantId));
      saveTag(tagRepository, removedTenantId);
      entityManager.flush();

      // Act
      tenantIsolationTestHelper.deleteCommittedTenants(removedTenantId);

      // Assert
      assertThat(committedTenantsAmong(tenantIds)).containsExactly(keptTenantId);
      assertThat(TestTransaction.isActive()).isTrue();
      String remainingScope = String.join(",", Tenant.DEFAULT_TENANT_UUID, keptTenantId);
      assertThat(currentAndAmbientScope()).isEqualTo(remainingScope + "|" + remainingScope);
    }

    @Test
    @DisplayName(
        "Given a test transaction aborted by a failing statement, should delete the committed"
            + " tenant")
    void given_abortedTestTransaction_should_deleteCommittedTenant() throws Exception {
      // Arrange
      List<String> tenantIds = commitTenants("cleanup-aborted");
      abortTestTransaction();

      // Act
      tenantIsolationTestHelper.deleteCommittedTenants(tenantIds.get(0));

      // Assert
      assertThat(committedTenantsAmong(tenantIds)).isEmpty();
      assertThat(TestTransaction.isActive()).isTrue();
    }
  }

  /** Creates the tenants, commits them, and starts a new test-managed transaction. */
  private List<String> commitTenants(String... names) throws Exception {
    List<String> tenantIds = new ArrayList<>();
    for (String name : names) {
      String tenantId = tenantIsolationTestHelper.createTenantWithCurrentUser(name).getId();
      tenantIds.add(tenantId);
      committedTenantIds.add(tenantId);
    }
    entityManager.flush();
    entityManager.clear();
    TestTransaction.flagForCommit();
    TestTransaction.end();
    TestTransaction.start();
    return tenantIds;
  }

  /**
   * Fails one statement, which leaves the test transaction aborted (PostgreSQL {@code 25P02}):
   * every later statement in it fails until it rolls back.
   */
  private void abortTestTransaction() {
    assertThatThrownBy(
            () ->
                entityManager
                    .createNativeQuery("SELECT 1 / 0")
                    .setFlushMode(FlushModeType.COMMIT)
                    .getSingleResult())
        .hasStackTraceContaining("division by zero");
    assertThatThrownBy(
            () ->
                entityManager
                    .createNativeQuery("SELECT 1")
                    .setFlushMode(FlushModeType.COMMIT)
                    .getSingleResult())
        .hasStackTraceContaining("current transaction is aborted");
  }

  /** The tenants among {@code tenantIds} that a connection outside the test transaction sees. */
  private List<String> committedTenantsAmong(List<String> tenantIds) throws SQLException {
    List<String> committed = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT tenant_id FROM tenants WHERE tenant_id = ?")) {
      for (String tenantId : tenantIds) {
        statement.setString(1, tenantId);
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            committed.add(resultSet.getString(1));
          }
        }
      }
    }
    return committed;
  }

  /**
   * The current and the ambient tenant scope of the test transaction, as {@code current|ambient}.
   */
  private String currentAndAmbientScope() {
    return (String)
        entityManager
            .createNativeQuery(AMBIENT_SCOPE_QUERY)
            .setFlushMode(FlushModeType.COMMIT)
            .getSingleResult();
  }
}
