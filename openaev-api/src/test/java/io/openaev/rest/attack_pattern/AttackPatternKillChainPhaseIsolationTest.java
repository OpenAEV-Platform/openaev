package io.openaev.rest.attack_pattern;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
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
 * {@code kill_chain_phases} is reached from {@link io.openaev.database.model.AttackPattern} through
 * a LAZY {@code @ManyToMany}, which bypasses {@code KillChainPhaseRepository} entirely. With
 * open-in-view the JSON rendering runs after the commit, and the tenant scope is transaction-local,
 * so a lazy load at rendering time would serialize an EMPTY phase list once the table is active —
 * the #7025 blind spot. This test pins the fix: the association is initialized inside the scoped
 * transaction, so a tenant sees its own phases and never another tenant's.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=kill_chain_phases")
@WithMockUser(isAdmin = true)
@DisplayName("kill_chain_phases isolation through the attack pattern association")
class AttackPatternKillChainPhaseIsolationTest extends IntegrationTest {

  private static final String TENANT_PATTERN_BY_ID =
      "/api/tenants/{tenantId}/attack_patterns/{attackPatternId}";
  private static final String TENANT_PATTERN_SEARCH =
      "/api/tenants/{tenantId}/attack_patterns/search";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String phaseA;
  private String phaseB;
  private String patternA;
  private String patternB;

  @BeforeEach
  void seedOnePatternWithOnePhasePerTenant() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("kcp-ap-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("kcp-ap-b").getId();
    phaseA = seedPhase(tenantA, "ap-phase-a", "AP9901");
    phaseB = seedPhase(tenantB, "ap-phase-b", "AP9902");
    patternA = seedPattern(tenantA, "ap-a", "T9901");
    patternB = seedPattern(tenantB, "ap-b", "T9902");
    link(patternA, phaseA);
    link(patternB, phaseB);
  }

  @Test
  @DisplayName("under tenant A's path: A's pattern exposes A's phase id")
  void ownPatternExposesItsPhase() throws Exception {
    String response =
        mvc.perform(get(TENANT_PATTERN_BY_ID, tenantA, patternA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(phaseA),
        "the attack pattern must expose its own tenant's kill chain phase; an empty list here means"
            + " the association was lazy-loaded outside the tenant scope");
  }

  @Test
  @DisplayName("under tenant A's path: B's pattern exposes none of B's phases")
  void crossTenantPatternExposesNoPhase() throws Exception {
    // attack_patterns is NOT tenant-active yet, so B's pattern itself is still reachable here. What
    // this activation guarantees is narrower and is exactly what is asserted: the phases hanging
    // off
    // it belong to B, so under A's scope the association resolves to nothing.
    String response =
        mvc.perform(get(TENANT_PATTERN_BY_ID, tenantA, patternB))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(response.contains(phaseB), "B's phase must not be readable under A's scope");
    assertFalse(response.contains(phaseA), "A's phase is not linked to B's pattern");
  }

  @Test
  @DisplayName("under tenant B's path: B's pattern exposes B's phase and never A's")
  void ownPatternNeverExposesAnotherTenantPhase() throws Exception {
    String response =
        mvc.perform(get(TENANT_PATTERN_BY_ID, tenantB, patternB))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(phaseB), "B's pattern must expose B's phase");
    assertFalse(response.contains(phaseA), "B's pattern must never expose A's phase");
  }

  @Test
  @DisplayName("search under tenant A's path: A's phase id is listed, B's is not")
  void searchExposesOnlyOwnTenantPhaseIds() throws Exception {
    // The search returns a DTO, so nothing hydrates the association any more: the phase ids come
    // from the projection, which reads kill_chain_phases and is therefore scoped. A regression
    // here means the projection lost its scope or its tenant correlation, and the page would carry
    // another tenant's phase ids.
    String response =
        mvc.perform(
                post(TENANT_PATTERN_SEARCH, tenantA)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(PaginationFixture.getDefault().size(50).build())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(phaseA),
        "A's own phase id must be listed; an empty list means the projection lost its scope");
    assertFalse(response.contains(phaseB), "B's phase id must never appear under A's scope");
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

  private String seedPattern(String tenantId, String name, String externalId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO attack_patterns"
                + " (attack_pattern_id, attack_pattern_name, attack_pattern_external_id,"
                + "  attack_pattern_stix_id, tenant_id)"
                + " VALUES (?1, ?2, ?3, ?4, ?5)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, externalId)
        .setParameter(4, "attack-pattern--" + UUID.randomUUID())
        .setParameter(5, tenantId)
        .executeUpdate();
    return id;
  }

  private void link(String attackPatternId, String phaseId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO attack_patterns_kill_chain_phases (attack_pattern_id, phase_id)"
                + " VALUES (?1, ?2)")
        .setParameter(1, attackPatternId)
        .setParameter(2, phaseId)
        .executeUpdate();
  }
}
