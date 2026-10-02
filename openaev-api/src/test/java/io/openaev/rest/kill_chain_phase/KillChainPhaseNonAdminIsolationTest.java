package io.openaev.rest.kill_chain_phase;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The isolation must not depend on the caller being an administrator. The other isolation tests run
 * as admin (RBAC bypassed); this one runs as a non-admin that is a member of two tenants and holds
 * only the capability that grants reading kill chain phases, so isolation — not RBAC — is what the
 * assertions exercise.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=kill_chain_phases")
@WithMockUser(isAdmin = false)
@DisplayName("kill_chain_phases isolation holds for a non-admin spanning two tenants")
class KillChainPhaseNonAdminIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String phaseA;
  private String phaseB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOnePhaseEach() throws Exception {
    Set<Capability> readPhases = Set.of(Capability.ACCESS_TENANT_SETTINGS);
    tenantA = tenantHelper.createTenantWithCapabilities("nonadmin-kcp-a", readPhases).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("nonadmin-kcp-b", readPhases).getId();
    phaseA = seedPhase(tenantA, "nonadmin-kcp-a", "TA9801");
    phaseB = seedPhase(tenantB, "nonadmin-kcp-b", "TA9802");
  }

  @Test
  @DisplayName("a non-admin searching under tenant A's path sees only A's phase")
  void searchUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/kill_chain_phases/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(phaseA), "A's phase must appear for the non-admin member of A");
    assertFalse(response.contains(phaseB), "B's phase must not leak into A's scope");
  }

  private String seedPhase(String tenantId, String name, String externalId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO kill_chain_phases"
                + " (phase_id, phase_name, phase_shortname, phase_kill_chain_name,"
                + "  phase_external_id, phase_stix_id, phase_order, tenant_id)"
                + " VALUES (?1, ?2, ?3, 'mitre-attack', ?4, ?5, 1, ?6)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, name)
        .setParameter(4, externalId)
        .setParameter(5, "x-mitre-tactic--" + UUID.randomUUID())
        .setParameter(6, tenantId)
        .executeUpdate();
    return id;
  }
}
