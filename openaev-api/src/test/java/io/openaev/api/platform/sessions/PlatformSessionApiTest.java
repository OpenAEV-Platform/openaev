package io.openaev.api.platform.sessions;

import static io.openaev.api.platform.sessions.PlatformSessionApi.PLATFORM_SESSIONS_URI;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.User;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class PlatformSessionApiTest extends IntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private io.openaev.utils.mockUser.TestUserHolder testUserHolder;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void setup() {
    // EE always active in all tests
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
  }

  @Nested
  @DisplayName("With no privileges")
  @WithMockUser
  class WithNoPrivileges {
    @Test
    @DisplayName("Can not read all active sessions")
    void canNotReadAllActiveSessions() throws Exception {
      mockMvc
          .perform(get(PLATFORM_SESSIONS_URI).contentType(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Can not delete specific active session")
    void canNotDeleteAllActiveSessions() throws Exception {
      mockMvc
          .perform(
              delete(PLATFORM_SESSIONS_URI + "/" + UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Can not delete all specific user active sessions")
    void canNotDeleteSpecificUserActiveSessions() throws Exception {
      User me = testUserHolder.get();
      mockMvc
          .perform(
              delete(PLATFORM_SESSIONS_URI + "/user/" + me.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("With privileges")
  @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_SESSIONS})
  class WithPrivileges {
    @Test
    @DisplayName("Can read all active sessions")
    void canReadAllActiveSessions() throws Exception {
      mockMvc
          .perform(get(PLATFORM_SESSIONS_URI).contentType(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Given no active session, then can not find session to delete")
    void given_noActiveSession_then_canNotFindSessionToDelete() throws Exception {
      mockMvc
          .perform(
              delete(PLATFORM_SESSIONS_URI + "/" + UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Given no active session, then invalidate user sessions anyway")
    void given_noActiveSession_then_canNotFindUserSessionsToDelete() throws Exception {
      User me = testUserHolder.get();
      mockMvc
          .perform(
              delete(PLATFORM_SESSIONS_URI + "/user/" + me.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }
  }
}
