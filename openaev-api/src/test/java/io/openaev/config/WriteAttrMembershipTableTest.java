package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.config.WriteAttrSignature.Relation;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * The membership table ({@code users_tenants}) stays under the write-attribution detector even
 * though the statement inspector never gates it: a membership row grants a user access to a tenant,
 * and the detector is the only check on the tenant such a row is written with. The detector leaves
 * out {@code tenants} alone, whose {@code tenant_id} is the row's own key.
 *
 * <p>Writes are issued straight from the test, so the gate waives them; the assertions read the raw
 * recorder, which records regardless. Everything runs in a rolled-back transaction.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@DisplayName("Write-attribution detector on the tenant membership table")
class WriteAttrMembershipTableTest extends IntegrationTest {

  private static final String MEMBERSHIP_TABLE = "users_tenants";

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private UserComposer userComposer;

  private String tenantB;
  private String userId;

  @BeforeEach
  void setUp() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-membership-b").getId();
    userId = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get().getId();
    entityManager.flush();
    entityManager.unwrap(Session.class).doWork(c -> WriteAttrDetectorTrigger.install(c));
  }

  @AfterEach
  void clear() {
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.stop();
  }

  @Nested
  @DisplayName("A membership write")
  class MembershipWrite {

    @Test
    @DisplayName("in the tenant of the scope is not flagged")
    void given_membershipInScope_should_notBeFlagged() {
      // -- PREPARE --
      startScoped(tenantB);

      // -- EXECUTE --
      tenantRepository.addUserToTenant(userId, tenantB);

      // -- ASSERT --
      WriteAttrDetectorRecorder.stop();
      assertFalse(
          flagged(MEMBERSHIP_TABLE, Relation.DEFAULT) || flagged(MEMBERSHIP_TABLE, Relation.OTHER),
          "a membership written in the scope's own tenant must stay silent, got "
              + WriteAttrDetectorRecorder.violations());
    }

    @Test
    @DisplayName("in the default tenant while the scope is another tenant is flagged")
    void given_membershipInDefaultTenantOutsideScope_should_beFlagged() {
      // -- PREPARE --
      // The shape a request writer produced when it took the ambient tenant instead of the
      // request's write scope.
      startScoped(tenantB);

      // -- EXECUTE --
      tenantRepository.addUserToTenant(userId, Tenant.DEFAULT_TENANT_UUID);

      // -- ASSERT --
      WriteAttrDetectorRecorder.stop();
      assertTrue(
          flagged(MEMBERSHIP_TABLE, Relation.DEFAULT),
          "expected a DEFAULT violation on users_tenants, got "
              + WriteAttrDetectorRecorder.violations());
    }

    @Test
    @DisplayName("in a tenant the scope does not hold is flagged")
    void given_membershipInOtherTenantOutsideScope_should_beFlagged() throws Exception {
      // -- PREPARE --
      String tenantC = tenantHelper.createTenant("wattr-membership-c").getId();
      startScoped(tenantB);

      // -- EXECUTE --
      tenantRepository.addUserToTenant(userId, tenantC);

      // -- ASSERT --
      WriteAttrDetectorRecorder.stop();
      assertTrue(
          flagged(MEMBERSHIP_TABLE, Relation.OTHER),
          "expected an OTHER violation on users_tenants, got "
              + WriteAttrDetectorRecorder.violations());
    }
  }

  @Test
  @DisplayName("The trigger is attached to every tenant table except tenants")
  void given_install_should_leaveOutTenantsAlone() {
    // -- EXECUTE --
    @SuppressWarnings("unchecked")
    List<String> watched =
        entityManager
            .createNativeQuery(
                "SELECT DISTINCT event_object_table FROM information_schema.triggers"
                    + " WHERE trigger_name = '_writeattr_trg' AND trigger_schema = 'public'")
            .getResultList();
    // Every base table of the schema with a tenant_id column, read independently of the install.
    @SuppressWarnings("unchecked")
    List<String> tenantTables =
        entityManager
            .createNativeQuery(
                "SELECT DISTINCT c.table_name FROM information_schema.columns c"
                    + " JOIN information_schema.tables t ON t.table_schema = c.table_schema"
                    + " AND t.table_name = c.table_name"
                    + " WHERE c.column_name = 'tenant_id' AND c.table_schema = 'public'"
                    + " AND t.table_type = 'BASE TABLE'")
            .getResultList();

    // -- ASSERT --
    assertEquals(Set.of("tenants"), WriteAttrDetectorTrigger.UNWATCHED_TABLES);
    assertTrue(
        tenantTables.containsAll(List.of(MEMBERSHIP_TABLE, "attackpath_graph_version", "tenants")),
        "the schema read must find the tenant tables this test is about, got " + tenantTables);
    Set<String> expected = new TreeSet<>(tenantTables);
    expected.removeAll(WriteAttrDetectorTrigger.UNWATCHED_TABLES);
    assertEquals(
        expected,
        new TreeSet<>(watched),
        "every tenant table but tenants must be watched, and nothing else");
  }

  private void startScoped(String scope) {
    WriteAttrDetectorRecorder.start();
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  private boolean flagged(String table, Relation relation) {
    return WriteAttrDetectorRecorder.violations().stream()
        .anyMatch(v -> table.equals(v.table()) && v.relation() == relation);
  }
}
