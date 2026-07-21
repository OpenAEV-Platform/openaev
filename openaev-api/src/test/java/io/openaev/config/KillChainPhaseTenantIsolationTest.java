package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Data-layer isolation proof for {@code kill_chain_phases}. With the table activated, the
 * transaction scope alone decides which tenant's kill chain phases a query sees and which a write
 * can touch.
 */
@SpringBootTest(properties = "openaev.tenant.active-tables=kill_chain_phases")
@DisplayName("Table activation readiness: kill_chain_phases")
class KillChainPhaseTenantIsolationTest extends TenantIsolationIntegrationTest {

  private static final String TENANT_A = "kcp-tenant-a";
  private static final String TENANT_B = "kcp-tenant-b";
  private static final String PHASE_A = "kcp-phase-a";
  private static final String PHASE_B = "kcp-phase-b";

  @BeforeEach
  void seedTwoTenantsWithOnePhaseEach() {
    seedTenant(TENANT_A);
    seedTenant(TENANT_B);
    insertPhase(PHASE_A, TENANT_A);
    insertPhase(PHASE_B, TENANT_B);
  }

  @Test
  @DisplayName("the active scope alone decides which tenant's phases a query returns")
  void scopeControlsRowVisibility() {
    setScope("");
    assertEquals(0, countVisible("kill_chain_phases", "phase_name", PHASE_A, PHASE_B));

    setScope(TENANT_A);
    assertEquals(1, countVisible("kill_chain_phases", "phase_name", PHASE_A, PHASE_B));
    assertEquals(PHASE_A, onlyVisible("kill_chain_phases", "phase_name", PHASE_A, PHASE_B));

    setScope(TENANT_B);
    assertEquals(1, countVisible("kill_chain_phases", "phase_name", PHASE_A, PHASE_B));
    assertEquals(PHASE_B, onlyVisible("kill_chain_phases", "phase_name", PHASE_A, PHASE_B));

    setScope(TENANT_A + "," + TENANT_B);
    assertEquals(2, countVisible("kill_chain_phases", "phase_name", PHASE_A, PHASE_B));
  }

  @Test
  @DisplayName("a write under one scope cannot reach another tenant's phase")
  void scopeProtectsWrites() {
    setScope(TENANT_A);
    assertEquals(
        0, deleteRow("kill_chain_phases", "phase_name", PHASE_B), "A cannot delete B's phase");
    assertEquals(
        1, deleteRow("kill_chain_phases", "phase_name", PHASE_A), "A can delete its own");

    setScope(TENANT_B);
    assertEquals(1, countVisible("kill_chain_phases", "phase_name", PHASE_A, PHASE_B));
    assertEquals(PHASE_B, onlyVisible("kill_chain_phases", "phase_name", PHASE_A, PHASE_B));
  }

  private void insertPhase(String name, String tenantId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO kill_chain_phases"
                + " (phase_id, phase_name, phase_shortname, phase_kill_chain_name,"
                + "  phase_external_id, phase_order, phase_created_at, phase_updated_at, tenant_id)"
                + " VALUES (gen_random_uuid(), :name, :name, 'mitre-attack',"
                + "  :name, 0, now(), now(), :tenant)")
        .setParameter("name", name)
        .setParameter("tenant", tenantId)
        .executeUpdate();
  }
}
