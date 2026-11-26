package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.config.WriteAttrDetectorRecorder.Violation;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
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
 * A managed entity changed through dirty checking is never handed to {@code persist} or {@code
 * merge}. Two things make such a write visible. The frame that loaded the entity is captured at
 * load time, so a change flushed later from a test frame is attributed to the production method
 * that loaded it. And when nobody captured the entity at all (it is loaded before the recording
 * window opened), the flush-time write with no production frame on its stack is reported as
 * unattributed and keyed on the test that flushed it, so it is triaged and not waived as
 * test-driven.
 *
 * <p>The class runs on the test profile's list, the production one. Under it the statement
 * inspector refuses an update that moves a row of an active table out of the transaction's scope,
 * so the only tenant rows a dirty change can write outside the scope are those of a table the list
 * leaves to the detector alone: {@code users_tenants}, a self-isolated membership table written by
 * Hibernate when the {@code tenants} collection of a loaded user changes. The loader is {@code
 * UserService.user}, a read by id with no save. The change is made here, so what the assertions pin
 * is that the attribution follows the frame that loaded the owner, not the frame that changed or
 * flushed it.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@DisplayName("Write-attribution: a dirty-checked change is attributed to its loader, or reported")
class WriteAttrDirtyUpdateReportingTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String TABLE = "users_tenants";
  private static final String LOADER = "io.openaev.service.UserService.user";
  private static final String UNATTRIBUTED_KEY =
      "unattributed(io.openaev.config.WriteAttrDirtyUpdateReportingTest$DirtyUpdate"
          + ".given_entityLoadedBeforeRecording_should_beReportedUnattributed)";

  @Autowired private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantB;
  private String userId;

  @BeforeEach
  void setUp() throws Exception {
    userComposer.reset();
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-dirty-b").getId();
    // The user is seeded with no membership while no scope is set, so the trigger stays silent.
    User user = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    entityManager.flush();
    entityManager.clear();
    userId = user.getId();
    entityManager.unwrap(Session.class).doWork(c -> WriteAttrDetectorTrigger.install(c));
  }

  @AfterEach
  void clear() {
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.stop();
    // The membership row written in the default tenant under scope B is this class's specimen,
    // asserted on in each test; the gate must not judge it again as a production write. Any other
    // violation is left to the gate.
    WriteAttrDetectorRecorder.discardViolations(violation -> TABLE.equals(violation.table()));
  }

  @Nested
  @DisplayName("Given a managed user whose membership collection changes without a save")
  class DirtyUpdate {

    @Test
    @DisplayName(
        "Given a production method loaded the entity, should attribute the change to the method")
    void given_methodLoadedTheEntity_should_attributeToTheMethod() {
      // Arrange
      TenantContext.clearCurrentTenant();
      WriteAttrDetectorRecorder.start();
      setScope(tenantB);
      User loaded = userService.user(userId);

      // Act: the default tenant joins the membership collection; nothing is saved.
      loaded.getTenants().add(entityManager.getReference(Tenant.class, DEFAULT_TENANT));
      String writtenTenant = readLinkTenantFromTestFrame();
      WriteAttrDetectorRecorder.stop();

      // Assert
      assertEquals(DEFAULT_TENANT, writtenTenant, "precondition: the row is in the default tenant");
      assertFalse(linkViolations().isEmpty(), "the trigger must see the dirty change");
      List<String> attributed = attributed();
      assertTrue(
          attributed.contains(TABLE + " DEFAULT " + LOADER),
          "a dirty change must be attributed to the production frame that loaded the entity;"
              + " attributed="
              + attributed
              + " violations="
              + linkViolations());
    }

    @Test
    @DisplayName("Given an entity loaded before recording, should be reported as unattributed")
    void given_entityLoadedBeforeRecording_should_beReportedUnattributed() {
      // Arrange: the user enters the persistence context outside the recording window, so no
      // frame is captured for it; the production read is then a cache hit that raises no load
      // event.
      TenantContext.clearCurrentTenant();
      WriteAttrDetectorRecorder.stop();
      userRepository.findById(userId).orElseThrow();
      WriteAttrDetectorRecorder.start();
      setScope(tenantB);
      User loaded = userService.user(userId);

      // Act
      loaded.getTenants().add(entityManager.getReference(Tenant.class, DEFAULT_TENANT));
      String writtenTenant = readLinkTenantFromTestFrame();
      WriteAttrDetectorRecorder.stop();

      // Assert
      assertEquals(DEFAULT_TENANT, writtenTenant, "precondition: the row is in the default tenant");
      assertFalse(linkViolations().isEmpty(), "the trigger must see the dirty change");
      List<String> attributed = attributed();
      assertTrue(
          attributed.contains(TABLE + " DEFAULT " + UNATTRIBUTED_KEY),
          "a flush-time write nobody captured must be reported as unattributed, keyed on the"
              + " flushing test, not waived; attributed="
              + attributed
              + " violations="
              + linkViolations());
    }
  }

  private List<Violation> linkViolations() {
    return WriteAttrDetectorRecorder.violations().stream()
        .filter(v -> TABLE.equals(v.table()))
        .toList();
  }

  private List<String> attributed() {
    return WriteAttrGateExtension.offendingSignatures(
        WriteAttrDetectorRecorder.violations(), Set.of());
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  /**
   * Forces the pending flush from this test method with a native query the inspector leaves alone
   * (no table), so the flush stack holds no production frame, then reads the membership row through
   * the transaction's JDBC connection.
   */
  private String readLinkTenantFromTestFrame() {
    entityManager.createNativeQuery("SELECT 1").getSingleResult();
    return jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM users_tenants WHERE user_id = ? AND tenant_id = ?",
        String.class,
        userId,
        DEFAULT_TENANT);
  }
}
