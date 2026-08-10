package io.openaev.api.attackpath;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The causal seed entrypoint: admin-only and gated behind the ATTACK_PATH feature, it creates a
 * simulation, writes a causal chain under it, and returns its id; the seeded graph is then readable
 * through the graph endpoint for that simulation.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH")
class AttackPathCausalSeedApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("POST /seed/causal seeds a chain an admin then reads via the graph endpoint")
  void admin_seeds_causal_chain_visible_via_graph() throws Exception {
    String body =
        mvc.perform(post(tenantUri(TENANT_PREFIX + "/attack-path/seed/causal")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.simulationId").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String sim = JsonPath.read(body, "$.simulationId");

    mvc.perform(get(tenantUri(TENANT_PREFIX + "/attack-path/simulations/" + sim + "/graph")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attackPathExecutions").isNotEmpty());
  }

  @Test
  @DisplayName("POST /seed/causal is forbidden for a non-admin")
  @WithMockUser(isAdmin = false)
  void non_admin_is_forbidden() throws Exception {
    mvc.perform(post(tenantUri(TENANT_PREFIX + "/attack-path/seed/causal")).with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /seed/causal?scenario=scaled fans out N footholds converging on the DC")
  void admin_seeds_the_scaled_fanout() throws Exception {
    String body =
        mvc.perform(
                post(tenantUri(TENANT_PREFIX + "/attack-path/seed/causal"))
                    .with(csrf())
                    .param("scenario", "scaled")
                    .param("footholds", "5"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String sim = JsonPath.read(body, "$.simulationId");

    // 5 footholds plus the shared DC they all funnel into.
    mvc.perform(get(tenantUri(TENANT_PREFIX + "/attack-path/simulations/" + sim + "/graph")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.endpoints").value(6));
  }

  @Test
  @DisplayName("POST /seed/causal?scenario=ransomware builds the deep-and-wide kill chain")
  void admin_seeds_the_ransomware_chain() throws Exception {
    String body =
        mvc.perform(
                post(tenantUri(TENANT_PREFIX + "/attack-path/seed/causal"))
                    .with(csrf())
                    .param("scenario", "ransomware")
                    .param("footholds", "4"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String sim = JsonPath.read(body, "$.simulationId");

    mvc.perform(get(tenantUri(TENANT_PREFIX + "/attack-path/simulations/" + sim + "/graph")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attackPathExecutions").isNotEmpty());
  }
}
