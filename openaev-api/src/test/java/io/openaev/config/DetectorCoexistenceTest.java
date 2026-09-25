package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.MitigationRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Both detectors must see every statement when both are enabled in one JVM. Each detector installs
 * a datasource-proxy listener through its own bean post-processor; if one wrapper wins the
 * datasource and the other is silently dropped, a detector looks armed but observes nothing. This
 * is the shape that ships in the shadow CI job, where {@code -Dopenaev.failclosed.detector=on} and
 * {@code -Dopenaev.writeattr.detector=on} run together.
 *
 * <p>The test wires both detectors on one context, then in a single unit does an unscoped read of
 * an active table (which the fail-closed detector must flag) and a wrong-tenant write (which the
 * write-attribution detector must flag). It fails if either recorder is empty, so it goes red on
 * the old skip-when-already-proxied wiring and green once the wrappers chain onto one proxy. {@code
 * mitigations} is activated so the inspector rewrites the read with {@code can_access_tenant}, the
 * signal the fail-closed detector keys on.
 */
@Transactional
@Import({FailClosedDetectorTestConfig.class, WriteAttrDetectorTestConfig.class})
@TestPropertySource(properties = "openaev.tenant.active-tables=mitigations")
@WithMockUser(isAdmin = true)
@DisplayName("Fail-closed and write-attribution detectors coexist in one JVM, each sees its signal")
class DetectorCoexistenceTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MitigationRepository mitigationRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("coexist-b").getId();
    entityManager
        .unwrap(Session.class)
        .doWork(c -> WriteAttrDetectorTrigger.install(c, TenantTables.selfIsolatedTables()));
  }

  @AfterEach
  void clearContext() {
    TenantContext.clearCurrentTenant();
    FailClosedAccessRecorder.stop();
    WriteAttrDetectorRecorder.stop();
  }

  @Test
  @DisplayName("an unscoped read and a wrong-tenant write in one unit are each recorded")
  void bothDetectorsRecordTheirOwnSignal() {
    FailClosedAccessRecorder.start();
    WriteAttrDetectorRecorder.start();

    // Fail-closed signal: read the active mitigations table with an empty scope. In production this
    // gate returns zero rows; the fail-closed detector must flag it.
    setScope("");
    mitigationRepository.findAll();

    // Write-attribution signal: stamp a scenario with the default tenant while the scope is B.
    setScope(tenantB);
    seedScenario(DEFAULT_TENANT);
    entityManager.flush();

    FailClosedAccessRecorder.stop();
    WriteAttrDetectorRecorder.stop();

    assertTrue(
        FailClosedAccessRecorder.violations().stream()
            .anyMatch(v -> v.sql() != null && v.sql().toLowerCase().contains("mitigations")),
        "coexistence hides the fail-closed detector: the unscoped mitigations read was not flagged");

    assertTrue(
        WriteAttrDetectorRecorder.violations().stream()
            .anyMatch(
                v ->
                    "scenarios".equals(v.table())
                        && DEFAULT_TENANT.equals(v.writtenTenant())
                        && tenantB.equals(v.scope())),
        "coexistence hides the write-attribution detector: the wrong-tenant write was not flagged");
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  private void seedScenario(String tenantId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
                + " VALUES (?1, ?2, ?3, ?4)")
        .setParameter(1, UUID.randomUUID().toString())
        .setParameter(2, "coexist-scenario")
        .setParameter(3, "planner@openaev.io")
        .setParameter(4, tenantId)
        .executeUpdate();
  }
}
