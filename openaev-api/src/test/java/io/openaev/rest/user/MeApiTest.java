package io.openaev.rest.user;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Me API tests")
public class MeApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Nested
  @DisplayName("GET /api/me")
  @WithMockUser(isAdmin = true)
  class GetMe {

    @Test
    @DisplayName("Should return current user info")
    void given_authenticatedUser_should_returnUserInfo() throws Exception {
      // -------- Arrange --------
      // No specific setup needed — uses the mock user from @WithMockUser

      // -------- Act & Assert --------
      mvc.perform(
              get(MeApi.ME_URI)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.user_id").isNotEmpty())
          .andExpect(jsonPath("$.user_email").isNotEmpty());
    }
  }

  @Nested
  @DisplayName("GET /api/me/tenants")
  @WithMockUser(isAdmin = true)
  class GetMyTenants {

    @Test
    @DisplayName("Should return list of tenants for current user")
    void given_authenticatedUser_should_returnTenantList() throws Exception {
      // -------- Arrange --------
      // No specific setup needed — uses the mock user from @WithMockUser

      // -------- Act & Assert --------
      mvc.perform(
              get(MeApi.ME_URI + "/tenants")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray());
    }
  }

  @Nested
  @DisplayName("GET /api/me/tokens")
  @WithMockUser(isAdmin = true)
  class GetMyTokens {

    @Test
    @DisplayName("Should return list of tokens for current user")
    void given_authenticatedUser_should_returnTokenList() throws Exception {
      // -------- Arrange --------
      // No specific setup needed — uses the mock user from @WithMockUser

      // -------- Act & Assert --------
      mvc.perform(
              get(MeApi.ME_URI + "/tokens")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray());
    }
  }

  @Nested
  @DisplayName("GET /api/logout")
  @WithMockUser(isAdmin = true)
  class Logout {

    @Test
    @DisplayName("Should return 200 OK")
    void given_authenticatedUser_should_logoutSuccessfully() throws Exception {
      // -------- Arrange --------
      // No specific setup needed — uses the mock user from @WithMockUser

      // -------- Act & Assert --------
      mvc.perform(
              get("/api/logout")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }
  }
}
