package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.MitigationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * WS1 - runtime fail-closed detector. Real stack (real Postgres + the real inspector). A read of a
 * v2-active table with no tenant scope must be flagged: in production it silently returns zero
 * rows. Its companion proves the detector does not false-positive when a scope is present.
 *
 * <p>{@code mitigations} is activated here so the real inspector treats it as v2-active (empty by
 * default in tests); the same allowlist feeds the detector's oracle.
 */
@Import(FailClosedDetectorTestConfig.class)
@TestPropertySource(properties = "openaev.tenant.active-tables=mitigations")
class FailClosedDetectorTest extends IntegrationTest {

  @Autowired private MitigationRepository mitigationRepository;

  @Test
  @DisplayName("an unscoped read of an active table is flagged as fail-closed")
  void unscoped_read_of_active_table_is_flagged() {
    FailClosedAccessRecorder.start();
    try {
      // mitigations is v2-active; no TxCtx here, so the aspect stamps no scope and the read runs
      // with an empty GUC (fail-closed: zero rows in production).
      mitigationRepository.findAll();
    } finally {
      FailClosedAccessRecorder.stop();
    }

    assertFalse(
        FailClosedAccessRecorder.violations().isEmpty(),
        "expected the unscoped read of the active mitigations table to be flagged as fail-closed");
  }

  @Test
  @Transactional
  @DisplayName("a correctly-scoped read of an active table is not flagged")
  void scoped_read_of_active_table_is_not_flagged() {
    // Stamp the scope on the current transaction's connection, exactly as the integration tests do;
    // the read below then carries a non-empty GUC, so it is a legitimate scoped access, not a leak.
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", Tenant.DEFAULT_TENANT_UUID)
        .getSingleResult();

    FailClosedAccessRecorder.start();
    try {
      mitigationRepository.findAll();
    } finally {
      FailClosedAccessRecorder.stop();
    }

    assertTrue(
        FailClosedAccessRecorder.violations().isEmpty(),
        "a read carrying a tenant scope must not be flagged as fail-closed");
  }
}
