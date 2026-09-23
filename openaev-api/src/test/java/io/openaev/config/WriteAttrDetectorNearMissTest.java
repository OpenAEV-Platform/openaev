package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.config.WriteAttrSignature.Relation;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * G7 near-miss coverage for the write-attribution detector: the shapes a proxy that parses SQL
 * would miss, plus the two boundary cases (a null tenant on a strict table, a table created after
 * the trigger was installed). Each writes a {@code tenant_id} the scope does not allow and asserts
 * the detector recorded it. Because detection is a DB trigger on {@code NEW.tenant_id}, statement
 * shape does not matter; these prove that empirically.
 *
 * <p>Non-vacuity is shown by a deliberate break: with the trigger's condition disabled every
 * assertion here goes red for the intended reason (evidence in the task report). All writes are
 * issued straight from this test, so the gate (which keys on a production entry frame) waives them;
 * the assertions read the raw recorder, which records regardless.
 *
 * <p>Everything runs in a rolled-back transaction: the trigger install, the constraint drop in the
 * null case and the table creation in the drift case are all undone at test end, so nothing here
 * changes the shared schema.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@DisplayName("Write-attribution detector fires on every write shape and on newly onboarded tables")
class WriteAttrDetectorNearMissTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-nm-b").getId();
    installTrigger();
  }

  @AfterEach
  void clear() {
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.stop();
  }

  @Test
  @DisplayName("INSERT ... SELECT: the tenant is a projection, not a bound value, and is flagged")
  void insertSelectIsFlagged() {
    startScoped(tenantB);
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
                + " SELECT :id, :name, :mail, :tenant")
        .setParameter("id", id)
        .setParameter("name", "nm-select-" + id)
        .setParameter("mail", "a@b.io")
        .setParameter("tenant", DEFAULT_TENANT)
        .executeUpdate();
    assertFlagged("scenarios", Relation.DEFAULT);
  }

  @Test
  @DisplayName(
      "INSERT ... ON CONFLICT DO UPDATE SET tenant_id: the upsert's update path is flagged")
  void upsertUpdatePathIsFlagged() {
    String id = seedScenario(tenantB); // in scope, not flagged
    startScoped(tenantB);
    entityManager
        .createNativeQuery(
            "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
                + " VALUES (:id, :name, :mail, :inscope)"
                + " ON CONFLICT (scenario_id) DO UPDATE SET tenant_id = :outscope")
        .setParameter("id", id)
        .setParameter("name", "nm-upsert-" + id)
        .setParameter("mail", "a@b.io")
        .setParameter("inscope", tenantB)
        .setParameter("outscope", DEFAULT_TENANT)
        .executeUpdate();
    assertFlagged("scenarios", Relation.DEFAULT);
  }

  @Test
  @DisplayName("UPDATE ... SET tenant_id: moving a row out of scope is flagged")
  void updateSetTenantIsFlagged() {
    String id = seedScenario(tenantB);
    startScoped(tenantB);
    entityManager
        .createNativeQuery("UPDATE scenarios SET tenant_id = :outscope WHERE scenario_id = :id")
        .setParameter("outscope", DEFAULT_TENANT)
        .setParameter("id", id)
        .executeUpdate();
    assertFlagged("scenarios", Relation.DEFAULT);
  }

  @Test
  @DisplayName("a native INSERT with the tenant supplied by the caller is flagged")
  void nativeInsertIsFlagged() {
    startScoped(tenantB);
    seedScenario(DEFAULT_TENANT);
    assertFlagged("scenarios", Relation.DEFAULT);
  }

  @Test
  @DisplayName("a null tenant on a strict table is flagged once the column is nullable")
  void nullTenantOnStrictTableIsFlagged() {
    // Every strict table is NOT NULL today, so TenantBaseListener's absence cannot yet produce a
    // null write. Drop the constraint in this rolled-back transaction to reach the state the
    // migration ends in (listener removed, column nullable), and prove the detector then flags it.
    entityManager
        .createNativeQuery("ALTER TABLE scenarios ALTER COLUMN tenant_id DROP NOT NULL")
        .executeUpdate();
    startScoped(tenantB);
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
                + " VALUES (:id, :name, :mail, NULL)")
        .setParameter("id", id)
        .setParameter("name", "nm-null-" + id)
        .setParameter("mail", "a@b.io")
        .executeUpdate();
    assertFlagged("scenarios", Relation.NULL);
  }

  @Test
  @DisplayName("a table created after install is not covered until the trigger is reinstalled")
  void tableCreatedAfterInstallNeedsReinstall() {
    entityManager
        .createNativeQuery(
            "CREATE TABLE writeattr_probe (probe_id varchar PRIMARY KEY, tenant_id varchar)")
        .executeUpdate();

    // Before reinstall: the install loop ran before this table existed, so it is uncovered.
    startScoped(tenantB);
    insertProbe(DEFAULT_TENANT);
    assertFalse(
        flagged("writeattr_probe", Relation.DEFAULT),
        "a table created after install must be uncovered until the trigger is reinstalled");
    WriteAttrDetectorRecorder.stop();

    // After reinstall: the schema-drift loop attaches the trigger to the new table.
    installTrigger();
    startScoped(tenantB);
    insertProbe(DEFAULT_TENANT);
    assertTrue(
        flagged("writeattr_probe", Relation.DEFAULT),
        "reinstalling the trigger must cover a newly created table");
  }

  @Test
  @DisplayName("an empty scope (deny-all) is not flagged: a documented limit, pinned")
  void given_emptyScope_should_notFlagTheWrite() {
    // Arrange: TxCtx.missing() sets app.current_tenants to '' and can_access_tenant refuses every
    // row, so a write made there is outside the scope by definition. The trigger stays silent on
    // purpose: the test utilities reset a transaction to '' after tenant onboarding, so raising the
    // empty scope flags the fixture writes of nearly every isolation test. Widening it again means
    // folding those fixture frames into the test-frame heuristic first, then re-freezing the
    // baseline.
    startScoped("");

    // Act
    seedScenario(tenantB);

    // Assert
    WriteAttrDetectorRecorder.stop();
    assertFalse(
        flagged("scenarios", Relation.OTHER),
        "the empty scope is a documented limit; widening it re-opens the fixture flood");
  }

  private void installTrigger() {
    entityManager
        .unwrap(Session.class)
        .doWork(c -> WriteAttrDetectorTrigger.install(c, TenantTables.selfIsolatedTables()));
  }

  private void startScoped(String scope) {
    WriteAttrDetectorRecorder.start();
    setScope(scope);
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  private String seedScenario(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
                + " VALUES (:id, :name, :mail, :tenant)")
        .setParameter("id", id)
        .setParameter("name", "nm-seed-" + id)
        .setParameter("mail", "a@b.io")
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private void insertProbe(String tenantId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO writeattr_probe (probe_id, tenant_id) VALUES (:id, :tenant)")
        .setParameter("id", UUID.randomUUID().toString())
        .setParameter("tenant", tenantId)
        .executeUpdate();
  }

  private void assertFlagged(String table, Relation relation) {
    WriteAttrDetectorRecorder.stop();
    assertTrue(
        flagged(table, relation),
        "expected a "
            + relation
            + " violation on "
            + table
            + ", got "
            + WriteAttrDetectorRecorder.violations());
  }

  private boolean flagged(String table, Relation relation) {
    return WriteAttrDetectorRecorder.violations().stream()
        .anyMatch(v -> table.equals(v.table()) && v.relation() == relation);
  }
}
