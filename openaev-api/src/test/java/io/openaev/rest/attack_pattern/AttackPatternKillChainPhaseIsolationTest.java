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
 * Phase ids served next to an attack pattern, on the by-id read and on the search. Both go through
 * {@code KillChainPhaseService.phaseIdsByAttackPatternId}, which projects them with {@code
 * KillChainPhaseRepository.findPhaseIdsByAttackPatternIds}: a JPQL query rooted on {@code
 * AttackPattern}, joined to the phases, correlated with {@code kcp.tenant.id = ap.tenant.id}. The
 * lazy {@code @ManyToMany} this class was first written for is no longer on the read path.
 *
 * <p>What that means for the claim this class can make: every assertion here holds with {@code
 * kill_chain_phases} removed from {@code openaev.tenant.active-tables}, because the query root
 * {@code AttackPattern} still carries the v1 {@code tenantFilter}, which restricts the projection
 * to the ambient tenant's patterns before the phases are reached. The activation also hides the
 * phases, through the join and the correlation, but it is not what makes these tests pass today, so
 * they must not be read as the proof that the table is active. That proof is {@code
 * KillChainPhaseHttpIsolationTest}, nine of whose assertions go red with the table disarmed.
 *
 * <p>What this class does pin, and is worth keeping: the phase ids served with a pattern are the
 * pattern's own tenant's, the list is never empty for the owner (the #7025 empty-list regression),
 * and the search projection agrees with the by-id read.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=kill_chain_phases,attack_patterns")
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
  @DisplayName("under tenant A's path: B's pattern is not reachable at all")
  void crossTenantPatternExposesNoPhase() throws Exception {
    // attack_patterns is tenant-active too, so the pattern itself is now out of A's scope and the
    // lookup is a 404. Before that activation this returned 200 with an empty phase list, which
    // was the narrower guarantee kill_chain_phases alone could give.
    mvc.perform(get(TENANT_PATTERN_BY_ID, tenantA, patternB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: B's pattern exposes B's phase and never A's")
  void ownPatternNeverExposesAnotherTenantPhase() throws Exception {
    // The correlation in the projection is what keeps A's phase off B's pattern, and it holds
    // whether or not the table is active: an own-tenant read, not an activation proof.
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
    // from the projection. A regression here means the projection lost its tenant correlation, or
    // the search page stopped being scoped, and the page would carry another tenant's phase ids.
    // Also green with the table disarmed, for the reason given in the class javadoc.
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
