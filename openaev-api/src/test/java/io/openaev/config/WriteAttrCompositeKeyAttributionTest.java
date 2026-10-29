package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.config.WriteAttrDetectorRecorder.Violation;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.executors.ExecutorService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * The connector tables ({@code executors}, {@code collectors}, {@code injectors}, {@code
 * injectors_contracts}) have a composite primary key {@code (id, tenant_id)} in the database and an
 * {@code @IdClass} on the entity, so the trigger cannot emit a single-column id and the Hibernate
 * id at {@code pre-insert} is a composite object. If the two sides do not agree on a row key, the
 * ask-time attribution is never found and the write falls back to the flush stack: flushed from a
 * test frame, a production write to one of the most active tables is waived as test-driven.
 *
 * <p>The production shape is {@code ExecutorService.register}, which builds an executor with the
 * tenant it is given and saves it; no controller writes these tables outside the request scope
 * today, so the service is driven directly under a scope that does not contain the written tenant.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@DisplayName("Write-attribution: a composite-key row is attributed to the asking production frame")
@WithoutTenantScope
class WriteAttrCompositeKeyAttributionTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String SERVICE = "io.openaev.executors.ExecutorService.register";

  @Autowired private ExecutorService executorService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-ck-b").getId();
    entityManager
        .unwrap(Session.class)
        .doWork(c -> WriteAttrDetectorTrigger.install(c, TenantTables.selfIsolatedTables()));
  }

  @AfterEach
  void clear() {
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.stop();
  }

  @Nested
  @DisplayName("Given a production write to a table whose primary key is (id, tenant_id)")
  class CompositeKeyWrite {

    @Test
    @DisplayName(
        "Given the insert flushed later from a test frame, should attribute it to the service")
    void given_compositeKeyInsertFlushedFromTestFrame_should_attributeToTheService()
        throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();
      WriteAttrDetectorRecorder.start();
      setScope(tenantB);
      String id = "wattr-exec-" + UUID.randomUUID();

      // Act: the service saves an executor stamped with the default tenant while the scope is B.
      executorService.register(
          DEFAULT_TENANT,
          id,
          "wattr-type-" + id,
          "wattr executor",
          null,
          null,
          null,
          null,
          new String[] {"Linux"},
          true);
      // No explicit flush: the pending insert is forced out by a native read from this test
      // method, so the flush stack holds no production frame.
      String writtenTenant = readTenantFromTestFrame(id);
      WriteAttrDetectorRecorder.stop();

      // Assert
      assertEquals(
          DEFAULT_TENANT, writtenTenant, "precondition: the row carries the default tenant");
      List<Violation> executorViolations =
          WriteAttrDetectorRecorder.violations().stream()
              .filter(v -> "executors".equals(v.table()))
              .toList();
      assertFalse(executorViolations.isEmpty(), "the trigger must see the executor write");
      List<String> attributed =
          WriteAttrGateExtension.offendingSignatures(
              WriteAttrDetectorRecorder.violations(), Set.of());
      assertTrue(
          attributed.contains("executors DEFAULT " + SERVICE),
          "the composite-key insert must be attributed to the asking service, not waived;"
              + " attributed="
              + attributed
              + " violations="
              + executorViolations);
    }

    @Test
    @DisplayName(
        "Given a second tenant reuses the same static id, should not resolve through the first"
            + " tenant's captured frame")
    void given_twoTenantsShareTheSameConnectorId_should_notCollideAcrossTenants() throws Exception {
      // Arrange: a static id shared across tenants, exactly the shape ConnectorCompositeId exists
      // for. The first row is written through the service (captured frame = SERVICE); the second,
      // same id, different tenant, is written by raw SQL straight from this test method (no
      // production frame on the stack at all).
      TenantContext.clearCurrentTenant();
      WriteAttrDetectorRecorder.start();
      setScope(tenantB);
      String sharedId = "wattr-shared-" + UUID.randomUUID();
      String tenantC = tenantHelper.createTenantWithCurrentUser("wattr-ck-c").getId();

      executorService.register(
          DEFAULT_TENANT,
          sharedId,
          "wattr-type-" + sharedId,
          "wattr executor A",
          null,
          null,
          null,
          null,
          new String[] {"Linux"},
          true);
      // Force the pending insert out from a test frame, as in the sibling test above.
      entityManager.createNativeQuery("SELECT 1").getSingleResult();

      jdbcTemplate.update(
          "INSERT INTO executors (executor_id, tenant_id, executor_name, executor_type,"
              + " executor_created_at, executor_updated_at) VALUES (?, ?, ?, ?, now(), now())",
          sharedId,
          tenantC,
          "wattr executor C",
          "wattr-type-" + sharedId);
      WriteAttrDetectorRecorder.stop();

      // Assert: the second row (tenant C) must never be attributed to the service frame captured
      // for the first row (tenant DEFAULT) just because they share the same non-tenant id.
      List<Violation> tenantCViolation =
          WriteAttrDetectorRecorder.violations().stream()
              .filter(v -> "executors".equals(v.table()))
              .filter(v -> tenantC.equals(v.writtenTenant()))
              .toList();
      assertFalse(tenantCViolation.isEmpty(), "the trigger must see the raw-SQL write to tenant C");
      assertTrue(
          tenantCViolation.stream().allMatch(v -> v.entryFrame() == null),
          "a synchronous test-driven write to a colliding id must not inherit the other tenant's"
              + " captured frame; violations="
              + tenantCViolation);
    }

    @Test
    @DisplayName(
        "Given a second, unrelated write to the same row later in the test, should not inherit the"
            + " first write's captured frame")
    void given_aSecondWriteToTheSameRowLaterInTheTest_should_notInheritTheFirstFrame()
        throws Exception {
      // Arrange: the service writes and flushes the row once (captured frame = SERVICE).
      TenantContext.clearCurrentTenant();
      WriteAttrDetectorRecorder.start();
      setScope(tenantB);
      String id = "wattr-reuse-" + UUID.randomUUID();
      executorService.register(
          DEFAULT_TENANT,
          id,
          "wattr-type-" + id,
          "wattr executor",
          null,
          null,
          null,
          null,
          new String[] {"Linux"},
          true);
      entityManager.createNativeQuery("SELECT 1").getSingleResult();

      // Act: a second, later write to the SAME row, issued directly by this test method (no
      // production frame at all). The row-level binding has no statement identity, so without
      // retiring it after the first resolution, this update would resolve through the stale
      // SERVICE binding instead of its own (empty) live stack.
      jdbcTemplate.update(
          "UPDATE executors SET executor_name = ? WHERE executor_id = ? AND tenant_id = ?",
          "wattr executor renamed by the test",
          id,
          DEFAULT_TENANT);
      WriteAttrDetectorRecorder.stop();

      // Assert: two violations for this id, the first attributed to the service, the second
      // (test-driven) carrying no production frame at all.
      List<Violation> executorViolations =
          WriteAttrDetectorRecorder.violations().stream()
              .filter(v -> "executors".equals(v.table()))
              .toList();
      assertEquals(
          2,
          executorViolations.size(),
          "the trigger must see both the insert and the update; violations=" + executorViolations);
      List<String> attributed =
          WriteAttrGateExtension.offendingSignatures(
              WriteAttrDetectorRecorder.violations(), Set.of());
      assertTrue(
          attributed.contains("executors DEFAULT " + SERVICE),
          "the first write must still be attributed to the service; attributed=" + attributed);
      assertTrue(
          executorViolations.stream()
              .filter(v -> v != executorViolations.get(0))
              .anyMatch(v -> v.entryFrame() == null),
          "the second, test-driven write to the same row must not reuse the first write's captured"
              + " frame; violations="
              + executorViolations);
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
   * (no table), so the flush stack holds no production frame, then reads the row through the
   * transaction's JDBC connection, which the statement inspector does not rewrite: under the
   * production active-tables list a scoped native read of an active table would not see a row of
   * another tenant, which is exactly the row this test wrote on purpose.
   */
  private String readTenantFromTestFrame(String executorId) {
    entityManager.createNativeQuery("SELECT 1").getSingleResult();
    return jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM executors WHERE executor_id = ?", String.class, executorId);
  }
}
