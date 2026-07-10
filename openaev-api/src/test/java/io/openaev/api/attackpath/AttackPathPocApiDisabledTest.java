package io.openaev.api.attackpath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With the default configuration (the {@code ATTACK_PATH_POC} preview feature is not enabled), the
 * POC controller is not registered and its routes return 404, so the feature is inert in a normal
 * deployment.
 */
@WithMockUser(isAdmin = true)
class AttackPathPocApiDisabledTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("The graph endpoint returns 404 when the POC preview feature is off")
  void graph_endpoint_not_registered_when_feature_off() throws Exception {
    mvc.perform(get(AttackPathPocApi.ATTACK_PATH_POC_URI + "/simulations/any/graph"))
        .andExpect(status().isNotFound());
  }
}
