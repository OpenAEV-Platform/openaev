package io.openaev.rest.attack_pattern;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The kill chain phase ids an attack pattern exposes come from {@code
 * KillChainPhaseService#phaseIdsByAttackPatternId}, a read of the v2-active {@code
 * kill_chain_phases} table. With no scope that read returns nothing, so every assertion here is on
 * a NON-EMPTY result: an empty list is the same observation as a correctly filtered one, and the
 * zero-row outcome is exactly what a lost scope would produce.
 *
 * <p>Two things {@link AttackPatternKillChainPhaseIsolationTest} does not cover, and that this
 * class adds. First, the non-prefixed route: it is the only one whose scope is not the tenant in
 * the URL but the caller's whole membership set, and it carries no ambient tenant of its own.
 * Second, a single attack pattern linked to one phase per tenant, with both tenants in the caller's
 * scope: the other tenant's phase passes the scope gate there, so only the correlation between the
 * phase tenant and the pattern tenant keeps it out.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=kill_chain_phases")
@WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
@DisplayName("attack pattern reads return the kill chain phases of the pattern's own tenant")
class AttackPatternKillChainPhaseRouteScopeTest extends IntegrationTest {

  private static final String ATTACK_PATTERNS = "/api/attack_patterns";
  private static final String TENANT_ATTACK_PATTERNS = "/api/tenants/{tenantId}/attack_patterns";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  private String otherTenant;
  private String patternId;
  private String defaultTenantPhase;
  private String otherTenantPhase;

  @BeforeEach
  void seedOnePatternLinkedToOnePhasePerTenant() throws Exception {
    otherTenant = tenantHelper.createTenantWithCurrentUser("ap-kcp-other").getId();
    patternId = seedAttackPattern();
    defaultTenantPhase = seedPhase(Tenant.DEFAULT_TENANT_UUID);
    otherTenantPhase = seedPhase(otherTenant);
    linkPhase(patternId, defaultTenantPhase);
    linkPhase(patternId, otherTenantPhase);
  }

  @Nested
  @DisplayName("read by id")
  class ReadById {

    @Test
    @DisplayName("on the tenant path: the pattern's own phase, not the other tenant's")
    void given_a_pattern_linked_to_two_tenants_phases_should_return_its_own_on_the_tenant_path()
        throws Exception {
      // Act
      List<String> phases =
          phasesOf(
              mvc.perform(
                      get(
                          TENANT_ATTACK_PATTERNS + "/{patternId}",
                          Tenant.DEFAULT_TENANT_UUID,
                          patternId))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString());

      // Assert
      assertThat(phases).containsExactly(defaultTenantPhase);
    }

    @Test
    @DisplayName("on the non-prefixed route: the pattern's own phase, not the other tenant's")
    void given_a_pattern_linked_to_two_tenants_phases_should_return_its_own_on_the_header_route()
        throws Exception {
      // Arrange: production starts a request with no ambient tenant; the fixtures leave one behind
      TenantContext.clearCurrentTenant();

      // Act
      List<String> phases =
          phasesOf(
              mvc.perform(get(ATTACK_PATTERNS + "/{patternId}", patternId))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString());

      // Assert
      assertThat(phases).containsExactly(defaultTenantPhase);
    }
  }

  @Nested
  @DisplayName("search")
  class Search {

    @Test
    @DisplayName("on the tenant path: the listed pattern carries its own tenant's phase")
    void given_a_pattern_linked_to_two_tenants_phases_should_list_its_own_on_the_tenant_path()
        throws Exception {
      // Act
      String body =
          mvc.perform(
                  post(TENANT_ATTACK_PATTERNS + "/search", Tenant.DEFAULT_TENANT_UUID)
                      .content(
                          asJsonString(
                              PaginationFixture.getDefault().textSearch(patternId).build()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(JsonPath.<List<String>>read(body, "$.content[0].attack_pattern_kill_chain_phases"))
          .containsExactly(defaultTenantPhase);
    }
  }

  private static List<String> phasesOf(String body) {
    return JsonPath.read(body, "$.attack_pattern_kill_chain_phases");
  }

  private String seedAttackPattern() {
    String id = "ap-kcp-" + UUID.randomUUID();
    entityManager
        .createNativeQuery(
            "INSERT INTO attack_patterns"
                + " (attack_pattern_id, attack_pattern_name, attack_pattern_external_id, tenant_id)"
                + " VALUES (:id, :id, :id, :tenant)")
        .setParameter("id", id)
        .setParameter("tenant", Tenant.DEFAULT_TENANT_UUID)
        .executeUpdate();
    return id;
  }

  private String seedPhase(String tenantId) {
    String id = "kcp-" + UUID.randomUUID();
    entityManager
        .createNativeQuery(
            "INSERT INTO kill_chain_phases"
                + " (phase_id, phase_name, phase_shortname, phase_kill_chain_name,"
                + "  phase_external_id, phase_order, tenant_id)"
                + " VALUES (:id, :id, :id, 'mitre-attack', :id, 1, :tenant)")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private void linkPhase(String attackPatternId, String phaseId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO attack_patterns_kill_chain_phases (attack_pattern_id, phase_id)"
                + " VALUES (:pattern, :phase)")
        .setParameter("pattern", attackPatternId)
        .setParameter("phase", phaseId)
        .executeUpdate();
  }
}
