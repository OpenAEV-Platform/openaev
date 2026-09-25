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
