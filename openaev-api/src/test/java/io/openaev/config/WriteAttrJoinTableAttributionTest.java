package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.config.WriteAttrDetectorRecorder.Violation;
import io.openaev.context.TenantContext;
import io.openaev.service.UserService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * A many-to-many link row is written by Hibernate's collection actions, which raise no entity
 * event: nothing is captured at ask time for the join table, and its composite key means the
 * trigger emits no row id either. The write can then only be attributed from the flush stack, and
 * flushed from a test frame it is waived as test-driven, although the method that asked for it is
 * known: the owner of the collection was loaded by that method.
 *
 * <p>The shape is {@code UserService.updateUser}: the user (the owner) is loaded by the service,
 * its {@code tenants} collection is replaced, and the {@code users_tenants} rows carry the added
 * tenant. The table is a join table that no active-table list holds, so the statement inspector
 * leaves the write alone and the detector is the only check on it.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@DisplayName(
    "Write-attribution: a join-table row is attributed to the method that changed its owner")
class WriteAttrJoinTableAttributionTest extends IntegrationTest {

  private static final String JOIN_TABLE = "users_tenants";
  private static final String UPDATER = "io.openaev.service.UserService.updateUser";

  @Autowired private UserService userService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserComposer userComposer;

  private String tenantB;
  private String tenantC;
  private String userId;
  private String userEmail;

  @BeforeEach
  void setUp() throws Exception {
    userComposer.reset();
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-join-b").getId();
    tenantC = tenantHelper.createTenant("wattr-join-c").getId();
    // The user is seeded with no membership while no scope is set, so the trigger stays silent.
    var user = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    entityManager.flush();
    entityManager.clear();
    userId = user.getId();
    userEmail = user.getEmail();
    entityManager.unwrap(Session.class).doWork(c -> WriteAttrDetectorTrigger.install(c));
  }

  @AfterEach
  void clear() {
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.stop();
  }

  @Nested
  @DisplayName("Given an update that adds a tenant to a user's membership collection")
  class CollectionWrite {

    @Test
    @DisplayName(
        "Given the link rows flushed later from a test frame, should attribute them to the method")
    void given_joinRowsFlushedFromTestFrame_should_attributeToTheUpdater() {
      // Arrange
      TenantContext.clearCurrentTenant();
      WriteAttrDetectorRecorder.start();
      setScope(tenantB);

      // Act
      userService.updateUser(
          userId,
          UserFixture.getUserInputWithTenants(
              userEmail, "Firstname", "Lastname", null, List.of(tenantC)));
      // No explicit flush: the pending link insert is forced out by a native read from this test
      // method, so the flush stack holds no production frame.
      String linkTenant = readLinkTenantFromTestFrame();
      WriteAttrDetectorRecorder.stop();

      // Assert
      assertEquals(tenantC, linkTenant, "precondition: the link row carries the added tenant");
      List<Violation> linkViolations =
          WriteAttrDetectorRecorder.violations().stream()
              .filter(v -> JOIN_TABLE.equals(v.table()))
              .toList();
      assertFalse(linkViolations.isEmpty(), "the trigger must see the link-row write");
      List<String> attributed =
          WriteAttrGateExtension.offendingSignatures(
              WriteAttrDetectorRecorder.violations(), Set.of());
      assertTrue(
          attributed.contains(JOIN_TABLE + " OTHER " + UPDATER),
          "the link row must be attributed to the method that changed its owner, not waived;"
              + " attributed="
              + attributed
              + " violations="
              + linkViolations);
    }
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  /**
   * Forces the pending flush from this test method with a native query the inspector leaves alone
   * (no table), so the flush stack holds no production frame, then reads the link row through the
   * transaction's JDBC connection.
   */
  private String readLinkTenantFromTestFrame() {
    entityManager.createNativeQuery("SELECT 1").getSingleResult();
    return jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM users_tenants WHERE user_id = ? AND tenant_id = ?",
        String.class,
        userId,
        tenantC);
  }
}
