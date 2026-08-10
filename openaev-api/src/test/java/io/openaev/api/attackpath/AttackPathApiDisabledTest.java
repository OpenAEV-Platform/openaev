package io.openaev.api.attackpath;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With the default configuration (the {@code ATTACK_PATH} preview feature is not enabled), the
 * runtime feature gate makes every attack-path route return 404, so the feature is inert in a
 * normal deployment.
 */
@WithMockUser(isAdmin = true)
class AttackPathApiDisabledTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("The graph endpoint returns 404 when the attack-path preview feature is off")
  void graph_endpoint_is_404_when_feature_off() throws Exception {
    mvc.perform(get(AttackPathApi.ATTACK_PATH_URI + "/simulations/any/graph"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("The causal seed endpoint returns 404 when the attack-path preview feature is off")
  void causal_seed_endpoint_is_404_when_feature_off() throws Exception {
    mvc.perform(post(AttackPathApi.ATTACK_PATH_URI + "/seed/causal").with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("The graph delta endpoint returns 404 when the attack-path preview feature is off")
  void graph_delta_endpoint_is_404_when_feature_off() throws Exception {
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/any/graph/delta").param("since", "0"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("The findings endpoint returns 404 when the attack-path preview feature is off")
  void findings_endpoint_is_404_when_feature_off() throws Exception {
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/any/findings")
                .param("category", "credentials"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "The execution detail endpoint returns 404 when the attack-path preview feature is off")
  void execution_detail_endpoint_is_404_when_feature_off() throws Exception {
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/any/execution").param("ref", "any"))
        .andExpect(status().isNotFound());
  }
}
